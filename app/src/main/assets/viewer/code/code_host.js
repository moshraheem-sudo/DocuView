(function () {
  "use strict";

  const params = new URLSearchParams(window.location.search);
  const docUrl = params.get("doc") || "";
  const isHtmlFile = params.get("html") === "1";
  const extParam = (params.get("ext") || "").toLowerCase();
  const initialNight = params.get("night") === "1";
  const initialDir = params.get("dir") || "auto";

  const langBadge = document.getElementById("lang-badge");
  const statsLabel = document.getElementById("stats-label");
  const htmlModeTabs = document.getElementById("html-mode-tabs");
  const btnModePreview = document.getElementById("btn-mode-preview");
  const btnModeSource = document.getElementById("btn-mode-source");
  const btnWrapToggle = document.getElementById("btn-wrap-toggle");
  const htmlPreviewPane = document.getElementById("html-preview-pane");
  const codeEditorPane = document.getElementById("code-editor-pane");
  const codeTbody = document.getElementById("code-tbody");

  let rawTextContent = "";
  let currentMode = isHtmlFile ? "preview" : "source";
  let wrapEnabled = true;
  let searchHits = [];
  let activeHitIndex = -1;

  if (initialNight) {
    document.body.classList.add("night-mode");
  }

  function detectLanguageLabel(ext, isHtml) {
    if (isHtml) return "HTML";
    const map = {
      kt: "Kotlin", kts: "Kotlin Script", java: "Java", py: "Python",
      js: "JavaScript", mjs: "JavaScript", ts: "TypeScript", tsx: "TSX", jsx: "JSX",
      json: "JSON", xml: "XML", css: "CSS", scss: "SCSS",
      cpp: "C++", cc: "C++", c: "C", h: "C/C++ Header", hpp: "C++ Header",
      cs: "C#", go: "Go", rs: "Rust", rb: "Ruby", php: "PHP", swift: "Swift",
      dart: "Dart", sh: "Shell", bash: "Bash", sql: "SQL",
      yaml: "YAML", yml: "YAML", toml: "TOML", ini: "INI",
      gradle: "Gradle", properties: "Properties", md: "Markdown", txt: "Text",
      csv: "CSV", log: "Log"
    };
    return map[ext] || (ext ? ext.toUpperCase() : "CODE");
  }

  function escapeHtml(str) {
    return str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function highlightLine(line, ext, isHtml) {
    if (!line.trim()) return escapeHtml(line) || " ";

    const trimmed = line.trimStart();
    if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
      return '<span class="tok-com">' + escapeHtml(line) + "</span>";
    }

    if (isHtml || ext === "xml" || ext === "svg") {
      const tagRe = /(<\/?[a-zA-Z0-9:_-]+(?:\s+[^>]*?)?>)/g;
      let result = "";
      let lastIdx = 0;
      let m;
      while ((m = tagRe.exec(line)) !== null) {
        if (m.index > lastIdx) {
          result += escapeHtml(line.slice(lastIdx, m.index));
        }
        result += '<span class="tok-tag">' + escapeHtml(m[0]) + "</span>";
        lastIdx = m.index + m[0].length;
      }
      if (lastIdx < line.length) {
        result += escapeHtml(line.slice(lastIdx));
      }
      return result;
    }

    const tokenRe = /("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|(@[A-Za-z_][A-Za-z0-9_]*)|\b(package|import|class|interface|object|fun|val|var|def|function|const|let|return|if|else|when|for|while|do|switch|case|break|continue|try|catch|finally|throw|private|public|protected|internal|override|suspend|data|sealed|enum|companion|true|false|null|void|int|long|float|double|boolean|String|List|Map|Set|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|JOIN|CREATE|TABLE)\b|\b(\d+(?:\.\d+)?)\b/g;
    let out = "";
    let lastIdx = 0;
    let match;
    while ((match = tokenRe.exec(line)) !== null) {
      if (match.index > lastIdx) {
        out += escapeHtml(line.slice(lastIdx, match.index));
      }
      const tokenText = escapeHtml(match[0]);
      if (match[1]) {
        out += '<span class="tok-str">' + tokenText + "</span>";
      } else if (match[2]) {
        out += '<span class="tok-ann">' + tokenText + "</span>";
      } else if (match[3]) {
        out += '<span class="tok-kw">' + tokenText + "</span>";
      } else if (match[4]) {
        out += '<span class="tok-num">' + tokenText + "</span>";
      }
      lastIdx = match.index + match[0].length;
    }
    if (lastIdx < line.length) {
      out += escapeHtml(line.slice(lastIdx));
    }
    return out;
  }

  function sanitizeHtmlForPreview(rawHtml) {
    try {
      const parser = new DOMParser();
      const doc = parser.parseFromString(rawHtml, "text/html");
      doc.querySelectorAll("script, iframe, object, embed, meta[http-equiv]").forEach(el => el.remove());
      doc.querySelectorAll("*").forEach(el => {
        Array.from(el.attributes).forEach(attr => {
          if (attr.name.toLowerCase().startsWith("on")) {
            el.removeAttribute(attr.name);
          }
        });
      });
      let stylesHtml = "";
      doc.querySelectorAll("style").forEach(styleEl => {
        stylesHtml += styleEl.outerHTML;
      });
      const bodyHtml = doc.body ? doc.body.innerHTML : rawHtml;
      return stylesHtml + bodyHtml;
    } catch (e) {
      return "<pre>" + escapeHtml(rawHtml) + "</pre>";
    }
  }

  function setViewMode(mode) {
    currentMode = mode;
    if (mode === "preview") {
      htmlPreviewPane.style.display = "block";
      codeEditorPane.style.display = "none";
      btnModePreview.classList.add("active");
      btnModeSource.classList.remove("active");
      btnWrapToggle.style.display = "none";
    } else {
      htmlPreviewPane.style.display = "none";
      codeEditorPane.style.display = "block";
      btnModePreview.classList.remove("active");
      btnModeSource.classList.add("active");
      btnWrapToggle.style.display = "inline-block";
    }
  }

  btnModePreview.addEventListener("click", function () {
    setViewMode("preview");
  });

  btnModeSource.addEventListener("click", function () {
    setViewMode("source");
  });

  btnWrapToggle.addEventListener("click", function () {
    wrapEnabled = !wrapEnabled;
    if (wrapEnabled) {
      codeEditorPane.classList.add("wrap-enabled");
      btnWrapToggle.classList.add("active");
    } else {
      codeEditorPane.classList.remove("wrap-enabled");
      btnWrapToggle.classList.remove("active");
    }
  });

  function renderCodeTable(text) {
    const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    const lines = normalized.split("\n");
    statsLabel.textContent = lines.length + " lines • " + normalized.length.toLocaleString() + " chars";

    const rowsHtml = [];
    for (let i = 0; i < lines.length; i++) {
      const ln = i + 1;
      const highlighted = highlightLine(lines[i], extParam, isHtmlFile);
      rowsHtml.push(
        '<tr><td class="ln-col">' + ln + '</td><td class="code-col">' + highlighted + "</td></tr>"
      );
    }
    codeTbody.innerHTML = rowsHtml.join("");
  }

  async function init() {
    langBadge.textContent = detectLanguageLabel(extParam, isHtmlFile);
    if (isHtmlFile) {
      htmlModeTabs.style.display = "inline-flex";
    }

    try {
      const res = await fetch(docUrl);
      if (!res.ok) throw new Error("HTTP " + res.status);
      rawTextContent = await res.text();

      renderCodeTable(rawTextContent);
      if (isHtmlFile) {
        htmlPreviewPane.innerHTML = sanitizeHtmlForPreview(rawTextContent);
        setViewMode("preview");
      } else {
        setViewMode("source");
      }

      setDirectionMode(initialDir);

      if (window.AndroidBridge && window.AndroidBridge.onDocumentLoaded) {
        window.AndroidBridge.onDocumentLoaded(1);
      }
    } catch (err) {
      if (window.AndroidBridge && window.AndroidBridge.onDocumentError) {
        window.AndroidBridge.onDocumentError(String(err && err.message ? err.message : err));
      }
    }
  }

  function clearSearchHighlights() {
    const marks = document.querySelectorAll("mark.search-hit");
    marks.forEach(m => {
      const parent = m.parentNode;
      if (parent) {
        parent.replaceChild(document.createTextNode(m.textContent), m);
        parent.normalize();
      }
    });
    searchHits = [];
    activeHitIndex = -1;
  }

  function highlightTextNodes(root, query) {
    const qLower = query.toLowerCase();
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
    const textNodes = [];
    let n;
    while ((n = walker.nextNode())) {
      if (n.parentElement && !n.parentElement.classList.contains("ln-col")) {
        if (n.nodeValue.toLowerCase().indexOf(qLower) !== -1) {
          textNodes.push(n);
        }
      }
    }

    textNodes.forEach(node => {
      const val = node.nodeValue;
      const frag = document.createDocumentFragment();
      let start = 0;
      let idx;
      while ((idx = val.toLowerCase().indexOf(qLower, start)) !== -1) {
        if (idx > start) {
          frag.appendChild(document.createTextNode(val.substring(start, idx)));
        }
        const mark = document.createElement("mark");
        mark.className = "search-hit";
        mark.textContent = val.substring(idx, idx + query.length);
        frag.appendChild(mark);
        searchHits.push(mark);
        start = idx + query.length;
      }
      if (start < val.length) {
        frag.appendChild(document.createTextNode(val.substring(start)));
      }
      node.parentNode.replaceChild(frag, node);
    });
  }

  function focusSearchHit(index) {
    if (!searchHits.length) {
      if (window.AndroidBridge && window.AndroidBridge.onSearchResult) {
        window.AndroidBridge.onSearchResult(0, 0, 1);
      }
      return;
    }
    searchHits.forEach(m => m.classList.remove("active"));
    activeHitIndex = ((index % searchHits.length) + searchHits.length) % searchHits.length;
    const hit = searchHits[activeHitIndex];
    hit.classList.add("active");
    hit.scrollIntoView({ behavior: "smooth", block: "center" });
    if (window.AndroidBridge && window.AndroidBridge.onSearchResult) {
      window.AndroidBridge.onSearchResult(activeHitIndex + 1, searchHits.length, 1);
    }
  }

  function setDirectionMode(dir) {
    if (dir === "rtl") {
      htmlPreviewPane.setAttribute("dir", "rtl");
      codeEditorPane.style.direction = "rtl";
      codeEditorPane.style.textAlign = "right";
    } else if (dir === "ltr") {
      htmlPreviewPane.setAttribute("dir", "ltr");
      codeEditorPane.style.direction = "ltr";
      codeEditorPane.style.textAlign = "left";
    } else {
      htmlPreviewPane.setAttribute("dir", "auto");
      codeEditorPane.style.direction = "ltr";
      codeEditorPane.style.textAlign = "left";
    }
  }

  window.DocuCode = {
    setZoom: function (z) {
      document.documentElement.style.setProperty("--code-zoom", String(z));
      if (window.AndroidBridge && window.AndroidBridge.onZoomChanged) {
        window.AndroidBridge.onZoomChanged(z);
      }
    },
    setNightMode: function (enabled) {
      if (enabled) {
        document.body.classList.add("night-mode");
      } else {
        document.body.classList.remove("night-mode");
      }
    },
    setDirectionMode: function (mode) {
      setDirectionMode(mode);
    },
    search: function (query) {
      clearSearchHighlights();
      if (!query || !query.trim()) {
        if (window.AndroidBridge && window.AndroidBridge.onSearchResult) {
          window.AndroidBridge.onSearchResult(0, 0, 1);
        }
        return;
      }
      const targetRoot = currentMode === "preview" ? htmlPreviewPane : codeTbody;
      highlightTextNodes(targetRoot, query.trim());
      if (searchHits.length > 0) {
        focusSearchHit(0);
      } else if (window.AndroidBridge && window.AndroidBridge.onSearchResult) {
        window.AndroidBridge.onSearchResult(0, 0, 1);
      }
    },
    findNext: function () {
      if (searchHits.length > 0) focusSearchHit(activeHitIndex + 1);
    },
    findPrev: function () {
      if (searchHits.length > 0) focusSearchHit(activeHitIndex - 1);
    },
    getSelectedText: function () {
      const sel = window.getSelection ? window.getSelection().toString() : "";
      if (window.AndroidBridge && window.AndroidBridge.onTextCopied) {
        window.AndroidBridge.onTextCopied(sel || rawTextContent);
      }
    },
    extractPageText: function () {
      const text = (rawTextContent || "").trim();
      if (window.AndroidBridge && window.AndroidBridge.onPageTextExtracted) {
        window.AndroidBridge.onPageTextExtracted(text ? rawTextContent : "__DOCUVIEW_NO_TEXT_LAYER__");
      }
    },
    extractAllPagesText: function () {
      this.extractPageText();
    }
  };

  init();
})();
