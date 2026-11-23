(function () {
  "use strict";

  const state = {
    zoom: 1.0,
    basePageWidth: 794,
    pageCount: 1,
    currentPage: 1,
    nightMode: false,
    dirMode: "auto", // "auto" | "rtl" | "ltr"
    matches: [],
    activeMatchIdx: -1,
    observer: null
  };

  const container = document.getElementById("word-container");
  const scaleHost = document.getElementById("word-scale-host");
  const commentsSection = document.getElementById("word-comments-section");
  const commentsPill = document.getElementById("word-comments-pill");

  function notify(fn, ...args) {
    if (window.AndroidBridge && typeof window.AndroidBridge[fn] === "function") {
      try {
        window.AndroidBridge[fn](...args);
      } catch (e) {
        console.error("Bridge error:", fn, e);
      }
    }
  }

  function applyScale() {
    const sections = container.querySelectorAll("section.docx");
    if (sections.length === 0) {
      scaleHost.style.transform = "none";
      scaleHost.style.zoom = String(state.zoom);
      scaleHost.style.width = "100%";
      scaleHost.style.height = "auto";
      scaleHost.style.marginLeft = "auto";
      scaleHost.style.marginRight = "auto";
      return;
    }
    const first = sections[0];
    const measuredW = first.offsetWidth || state.basePageWidth || 794;
    state.basePageWidth = measuredW;
    const availW = Math.max(260, window.innerWidth - 16);
    const fitScale = Math.min(1.15, availW / measuredW);
    const effectiveScale = fitScale * state.zoom;

    scaleHost.style.transform = "none";
    scaleHost.style.width = measuredW + "px";
    scaleHost.style.height = "auto";
    scaleHost.style.zoom = String(effectiveScale);
    scaleHost.style.marginLeft = "auto";
    scaleHost.style.marginRight = "auto";
  }

  function setupPageObserver() {
    if (state.observer) state.observer.disconnect();
    const sections = Array.from(container.querySelectorAll("section.docx"));
    state.pageCount = Math.max(1, sections.length);

    if (sections.length === 0) return;

    state.observer = new IntersectionObserver(
      (entries) => {
        let bestIdx = state.currentPage - 1;
        let bestRatio = 0;
        entries.forEach((entry) => {
          if (entry.isIntersecting && entry.intersectionRatio > bestRatio) {
            bestRatio = entry.intersectionRatio;
            const idx = sections.indexOf(entry.target);
            if (idx >= 0) bestIdx = idx;
          }
        });
        if (bestRatio > 0.1 && bestIdx + 1 !== state.currentPage) {
          state.currentPage = bestIdx + 1;
          notify("onPageChanged", state.currentPage, state.pageCount);
        }
      },
      { threshold: [0.1, 0.4, 0.75] }
    );
    sections.forEach((s) => state.observer.observe(s));
  }

  async function renderOpenXmlFallback(buf) {
    if (typeof JSZip === "undefined") return false;
    const zip = await JSZip.loadAsync(buf);
    let docEntry = zip.file("word/document.xml");
    if (!docEntry) {
      const candidates = Object.keys(zip.files).filter((k) => k.toLowerCase().endsWith(".xml") && k.toLowerCase().includes("word/document"));
      if (candidates.length > 0) docEntry = zip.file(candidates[0]);
    }
    if (!docEntry) return false;
    const xmlStr = await docEntry.async("string");
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlStr, "application/xml");
    const card = document.createElement("div");
    card.className = "legacy-doc-page";
    const pNodes = xmlDoc.getElementsByTagNameNS("*", "p");
    for (let i = 0; i < pNodes.length; i++) {
      const tNodes = pNodes[i].getElementsByTagNameNS("*", "t");
      let line = "";
      for (let j = 0; j < tNodes.length; j++) {
        line += tNodes[j].textContent || "";
      }
      if (line.trim()) {
        const p = document.createElement("p");
        p.textContent = line.trim();
        card.appendChild(p);
      }
    }
    if (!card.hasChildNodes()) return false;
    container.innerHTML = "";
    container.appendChild(card);
    return true;
  }

  async function extractAndRenderComments(buf) {
    if (!commentsSection || !commentsPill || typeof JSZip === "undefined") return;
    commentsSection.classList.remove("visible");
    commentsSection.innerHTML = "";
    commentsPill.classList.remove("visible");

    try {
      const zip = await JSZip.loadAsync(buf);
      const commentFileKey = Object.keys(zip.files).find((k) => k.toLowerCase() === "word/comments.xml");
      if (!commentFileKey) return;

      const commentsXmlStr = await zip.file(commentFileKey).async("string");
      const parser = new DOMParser();
      const commentsDoc = parser.parseFromString(commentsXmlStr, "application/xml");
      const commentNodes = Array.from(commentsDoc.getElementsByTagNameNS("*", "comment"));
      if (commentNodes.length === 0) return;

      // Extract quoted text snippets between commentRangeStart and commentRangeEnd in document.xml
      const quoteById = new Map();
      const docFileKey = Object.keys(zip.files).find((k) => k.toLowerCase() === "word/document.xml");
      if (docFileKey) {
        try {
          const docXmlStr = await zip.file(docFileKey).async("string");
          const docXml = parser.parseFromString(docXmlStr, "application/xml");
          const activeIds = new Set();
          const walker = docXml.createTreeWalker(docXml.documentElement, NodeFilter.SHOW_ELEMENT);
          let el;
          while ((el = walker.nextNode())) {
            const local = el.localName || el.nodeName.replace(/^.*:/, "");
            if (local === "commentRangeStart") {
              const cid = el.getAttribute("w:id") || el.getAttribute("id");
              if (cid != null) activeIds.add(cid);
            } else if (local === "commentRangeEnd") {
              const cid = el.getAttribute("w:id") || el.getAttribute("id");
              if (cid != null) activeIds.delete(cid);
            } else if ((local === "t" || local === "delText") && activeIds.size > 0) {
              const txt = el.textContent || "";
              if (txt) {
                activeIds.forEach((cid) => {
                  const prev = quoteById.get(cid) || "";
                  if (prev.length < 120) quoteById.set(cid, prev + txt);
                });
              }
            }
          }
        } catch (_) {}
      }

      const parsedComments = commentNodes.map((node, idx) => {
        const id = node.getAttribute("w:id") || node.getAttribute("id") || String(idx + 1);
        const author = node.getAttribute("w:author") || node.getAttribute("author") || "Reviewer";
        const rawDate = node.getAttribute("w:date") || node.getAttribute("date") || "";
        let dateStr = "";
        if (rawDate) {
          const d = new Date(rawDate);
          dateStr = isNaN(d.getTime()) ? rawDate : d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
        }
        const pNodes = Array.from(node.getElementsByTagNameNS("*", "p"));
        const bodyLines = [];
        if (pNodes.length > 0) {
          pNodes.forEach((p) => {
            const tNodes = Array.from(p.getElementsByTagNameNS("*", "t"));
            const line = tNodes.map((t) => t.textContent || "").join("").trim();
            if (line) bodyLines.push(line);
          });
        } else {
          const txt = (node.textContent || "").trim();
          if (txt) bodyLines.push(txt);
        }
        const quote = (quoteById.get(id) || "").trim();
        return {
          id,
          author,
          dateStr,
          quote: quote.length > 100 ? quote.slice(0, 97) + "…" : quote,
          text: bodyLines.join("\n").trim() || "(—)"
        };
      });

      const isArUi = /^ar/i.test(navigator.language || "") || state.dirMode === "rtl" || parsedComments.some((c) => /[\u0600-\u06FF]/.test(c.text));
      const titleLabel = isArUi
        ? `💬 تعليقات المراجعة (${parsedComments.length})`
        : `💬 Review Comments (${parsedComments.length})`;

      commentsPill.textContent = titleLabel;
      commentsPill.classList.add("visible");
      commentsPill.onclick = () => {
        commentsSection.scrollIntoView({ behavior: "smooth", block: "start" });
      };

      const header = document.createElement("div");
      header.className = "comments-section-header";
      header.dir = isArUi ? "rtl" : "ltr";
      header.textContent = titleLabel;
      commentsSection.appendChild(header);

      const inlineRefs = Array.from(container.querySelectorAll(".docx-comment-ref"));
      parsedComments.forEach((c, index) => {
        const card = document.createElement("div");
        card.className = "comment-card";
        card.dir = "auto";

        const meta = document.createElement("div");
        meta.className = "comment-card-meta";
        const authorSpan = document.createElement("span");
        authorSpan.className = "comment-author-badge";
        authorSpan.textContent = `#${index + 1} • ${c.author}`;
        meta.appendChild(authorSpan);
        if (c.dateStr) {
          const dateSpan = document.createElement("span");
          dateSpan.className = "comment-date-label";
          dateSpan.textContent = c.dateStr;
          meta.appendChild(dateSpan);
        }
        card.appendChild(meta);

        if (c.quote) {
          const quoteDiv = document.createElement("div");
          quoteDiv.className = "comment-quote-snippet";
          quoteDiv.textContent = `“${c.quote}”`;
          card.appendChild(quoteDiv);
        }

        const bodyDiv = document.createElement("div");
        bodyDiv.className = "comment-body-text";
        bodyDiv.textContent = c.text;
        card.appendChild(bodyDiv);

        card.addEventListener("click", () => {
          const refEl = inlineRefs[index];
          if (refEl) {
            refEl.scrollIntoView({ behavior: "smooth", block: "center" });
          }
        });

        commentsSection.appendChild(card);
      });

      commentsSection.classList.add("visible");
    } catch (_) {}
  }

  function wireTouchCommentPopovers() {
    const refs = Array.from(container.querySelectorAll(".docx-comment-ref"));
    refs.forEach((ref) => {
      const popover = ref.nextElementSibling;
      if (!popover || !popover.classList.contains("docx-comment-popover")) return;
      ref.addEventListener("click", (e) => {
        e.preventDefault();
        e.stopPropagation();
        const wasOpen = popover.classList.contains("open");
        container.querySelectorAll(".docx-comment-popover.open").forEach((p) => p.classList.remove("open"));
        if (!wasOpen) popover.classList.add("open");
      });
    });
    document.addEventListener("click", (e) => {
      if (!e.target.closest || (!e.target.closest(".docx-comment-ref") && !e.target.closest(".docx-comment-popover"))) {
        container.querySelectorAll(".docx-comment-popover.open").forEach((p) => p.classList.remove("open"));
      }
    });
  }

  async function loadDocx(docUrl, nightMode, dirMode) {
    try {
      setNightMode(!!nightMode);
      setDirectionMode(dirMode || "auto");

      const res = await fetch(docUrl);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const buf = await res.arrayBuffer();

      container.innerHTML = "";
      try {
        await docx.renderAsync(buf, container, null, {
          className: "docx",
          inWrapper: true,
          ignoreWidth: false,
          ignoreHeight: true,
          ignoreFonts: false,
          breakPages: true,
          ignoreLastRenderedPageBreak: false,
          experimental: false,
          trimXmlDeclaration: true,
          useBase64URL: true,
          renderHeaders: true,
          renderFooters: true,
          renderFootnotes: true,
          renderEndnotes: true,
          renderComments: true
        });
      } catch (renderErr) {
        const ok = await renderOpenXmlFallback(buf);
        if (!ok) throw renderErr;
      }

      wireTouchCommentPopovers();
      await extractAndRenderComments(buf);

      const sections = container.querySelectorAll("section.docx");
      state.pageCount = Math.max(1, sections.length);
      state.currentPage = 1;

      requestAnimationFrame(() => {
        applyScale();
        setupPageObserver();
        notify("onDocumentLoaded", state.pageCount);
        notify("onPageChanged", 1, state.pageCount);
      });
    } catch (err) {
      notify("onDocumentError", err && err.message ? err.message : String(err));
    }
  }

  async function loadLegacyDoc(docUrl, nightMode, dirMode) {
    try {
      setNightMode(!!nightMode);
      setDirectionMode(dirMode || "auto");
      const res = await fetch(docUrl);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();

      container.innerHTML = "";
      const card = document.createElement("div");
      card.className = "legacy-doc-page";
      const paragraphs = Array.isArray(data.paragraphs) ? data.paragraphs : [];
      if (paragraphs.length === 0) {
        const p = document.createElement("p");
        p.textContent = "No readable text stream could be extracted from this legacy .doc binary.";
        card.appendChild(p);
      } else {
        paragraphs.forEach((text) => {
          const p = document.createElement("p");
          p.textContent = text;
          card.appendChild(p);
        });
      }
      container.appendChild(card);
      state.pageCount = 1;
      state.currentPage = 1;
      notify("onDocumentLoaded", 1);
      notify("onPageChanged", 1, 1);
    } catch (err) {
      notify("onDocumentError", err && err.message ? err.message : String(err));
    }
  }

  function clearHighlights() {
    state.matches.forEach((mark) => {
      const parent = mark.parentNode;
      if (parent) {
        parent.replaceChild(document.createTextNode(mark.textContent), mark);
        parent.normalize();
      }
    });
    state.matches = [];
    state.activeMatchIdx = -1;
  }

  function search(query) {
    clearHighlights();
    const q = (query || "").trim();
    if (!q) {
      notify("onSearchResult", 0, 0, state.currentPage);
      return;
    }

    const needle = q.toLowerCase();
    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
      acceptNode(node) {
        if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
        const p = node.parentNode;
        if (p && (p.nodeName === "STYLE" || p.nodeName === "SCRIPT")) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    const textNodes = [];
    let current;
    while ((current = walker.nextNode())) {
      textNodes.push(current);
    }

    textNodes.forEach((node) => {
      const text = node.nodeValue;
      const lower = text.toLowerCase();
      if (!lower.includes(needle)) return;

      const frag = document.createDocumentFragment();
      let lastIdx = 0;
      let idx = lower.indexOf(needle);
      while (idx !== -1) {
        if (idx > lastIdx) {
          frag.appendChild(document.createTextNode(text.slice(lastIdx, idx)));
        }
        const mark = document.createElement("mark");
        mark.className = "docu-match";
        mark.textContent = text.slice(idx, idx + q.length);
        frag.appendChild(mark);
        state.matches.push(mark);
        lastIdx = idx + q.length;
        idx = lower.indexOf(needle, lastIdx);
      }
      if (lastIdx < text.length) {
        frag.appendChild(document.createTextNode(text.slice(lastIdx)));
      }
      node.parentNode.replaceChild(frag, node);
    });

    if (state.matches.length > 0) {
      state.activeMatchIdx = 0;
      activateMatch(0);
    } else {
      notify("onSearchResult", 0, 0, state.currentPage);
    }
  }

  function activateMatch(index) {
    state.matches.forEach((m, i) => m.classList.toggle("active", i === index));
    const target = state.matches[index];
    if (target) {
      target.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    notify("onSearchResult", index + 1, state.matches.length, state.currentPage);
  }

  function stepSearch(delta) {
    if (state.matches.length === 0) return;
    state.activeMatchIdx = (state.activeMatchIdx + delta + state.matches.length) % state.matches.length;
    activateMatch(state.activeMatchIdx);
  }

  function goToPage(pageNum) {
    const sections = container.querySelectorAll("section.docx");
    if (sections.length === 0) return;
    const idx = Math.max(0, Math.min(sections.length - 1, (parseInt(pageNum, 10) || 1) - 1));
    sections[idx].scrollIntoView({ behavior: "smooth", block: "start" });
    state.currentPage = idx + 1;
    notify("onPageChanged", state.currentPage, state.pageCount);
  }

  function setZoom(zoomFactor) {
    state.zoom = Math.max(0.5, Math.min(3.0, parseFloat(zoomFactor) || 1.0));
    applyScale();
    notify("onZoomChanged", state.zoom);
  }

  function setNightMode(enabled) {
    state.nightMode = !!enabled;
    document.body.classList.toggle("night-mode", state.nightMode);
  }

  function setDirectionMode(mode) {
    state.dirMode = mode || "auto";
    document.body.classList.remove("dir-auto", "dir-rtl", "dir-ltr");
    document.body.classList.add("dir-" + state.dirMode);
  }

  let lastSelectedText = "";
  const copyPill = document.createElement("div");
  copyPill.id = "docu-copy-pill";
  copyPill.textContent = /^ar/i.test(navigator.language || "") ? "📋 نسخ" : "📋 Copy";
  function handleCopyPillTap(e) {
    e.preventDefault();
    e.stopPropagation();
    const currentSel = window.getSelection ? window.getSelection().toString().trim() : "";
    const textToCopy = currentSel || lastSelectedText;
    if (textToCopy) {
      notify("onTextCopied", textToCopy);
      copyPill.classList.remove("visible");
    }
  }
  copyPill.addEventListener("touchend", handleCopyPillTap);
  copyPill.addEventListener("click", handleCopyPillTap);
  document.body.appendChild(copyPill);

  document.addEventListener("selectionchange", () => {
    const sel = window.getSelection ? window.getSelection().toString().trim() : "";
    if (sel.length > 0) {
      lastSelectedText = sel;
      copyPill.textContent = (/^ar/i.test(navigator.language || "") || /[\u0600-\u06FF]/.test(sel)) ? "📋 نسخ" : "📋 Copy";
      copyPill.classList.add("visible");
    } else {
      setTimeout(() => {
        const check = window.getSelection ? window.getSelection().toString().trim() : "";
        if (!check) copyPill.classList.remove("visible");
      }, 280);
    }
  });

  function extractTextFromRoot(root) {
    if (!root) return "";
    const blocks = root.querySelectorAll("p, h1, h2, h3, h4, h5, h6, li, tr");
    const lines = [];
    blocks.forEach((el) => {
      if (el.tagName === "TR") {
        const cells = Array.from(el.querySelectorAll("th, td"))
          .map((c) => (c.innerText || c.textContent || "").replace(/\s+/g, " ").trim())
          .filter(Boolean);
        if (cells.length > 0) lines.push(cells.join("  |  "));
      } else if (!el.closest("tr")) {
        const t = (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
        if (t) lines.push(t);
      }
    });
    const combined = lines.length > 0
      ? lines.join("\n\n")
      : (root.innerText || root.textContent || "").trim();
    return combined.trim();
  }

  function extractPageText(explicitPage) {
    const sections = container.querySelectorAll("section.docx");
    let fullText = "";
    if (sections.length > 0) {
      const req = parseInt(explicitPage, 10);
      const targetPage = Math.max(1, Math.min(sections.length, req > 0 ? req : (state.currentPage || 1)));
      fullText = extractTextFromRoot(sections[targetPage - 1]);
    }
    if (!fullText) {
      fullText = extractTextFromRoot(container);
    }
    notify("onPageTextExtracted", fullText ? fullText : "__DOCUVIEW_NO_TEXT_LAYER__");
    return fullText;
  }

  function extractAllPagesText() {
    const sections = container.querySelectorAll("section.docx");
    let combined = "";
    if (sections.length > 1) {
      const parts = [];
      sections.forEach((sec, idx) => {
        const txt = extractTextFromRoot(sec);
        if (txt) parts.push(`--- #${idx + 1} ---\n${txt}`);
      });
      combined = parts.join("\n\n").trim();
    }
    if (!combined) {
      combined = extractTextFromRoot(container);
    }
    notify("onPageTextExtracted", combined ? combined : "__DOCUVIEW_NO_TEXT_LAYER__");
    return combined;
  }

  function getSelectedText() {
    const liveSel = window.getSelection ? window.getSelection().toString().trim() : "";
    const chosen = liveSel || lastSelectedText || "";
    if (chosen) {
      notify("onTextCopied", chosen);
      lastSelectedText = "";
      copyPill.classList.remove("visible");
      return chosen;
    }
    extractPageText(state.currentPage);
    return "";
  }

  window.addEventListener("resize", () => applyScale());

  window.DocuWord = {
    loadDocx,
    loadLegacyDoc,
    goToPage,
    setZoom,
    setNightMode,
    setDirectionMode,
    search,
    findNext: () => stepSearch(1),
    findPrev: () => stepSearch(-1),
    getSelectedText,
    extractPageText,
    extractAllPagesText
  };

  const params = new URLSearchParams(window.location.search);
  const docParam = params.get("doc");
  const isLegacy = params.get("legacy") === "1";
  if (docParam) {
    if (isLegacy) {
      loadLegacyDoc(docParam, params.get("night") === "1", params.get("dir") || "auto");
    } else {
      loadDocx(docParam, params.get("night") === "1", params.get("dir") || "auto");
    }
  }
})();
