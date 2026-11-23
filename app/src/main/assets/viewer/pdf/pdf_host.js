(function () {
  "use strict";

  pdfjsLib.GlobalWorkerOptions.workerSrc = "./pdf.worker.min.js";

  const state = {
    pdfDoc: null,
    pageCount: 0,
    currentPage: 1,
    zoom: 1.0,
    fitMode: "width", // "width" | "page" | "custom"
    rotation: 0,
    continuous: true,
    nightMode: false,
    renderQueue: new Set(),
    renderedPages: new Map(), // pageNum -> { scale, rotation }
    pageDimensions: new Map(), // pageNum -> { width, height }
    pageTexts: new Map(), // pageNum -> { items, fullText, spans }
    searchQuery: "",
    matches: [], // { pageNum, itemIndex, startOffset, length, rect }
    activeMatchIdx: -1,
    observer: null,
    renderToken: 0,
    visualRefineTimer: null,
    // PDF Annotation & Drawing state
    annotationTool: "none", // "none" | "pen" | "highlighter" | "underline" | "rect" | "eraser"
    annotationColor: "#E11D48",
    annotationSize: 3.5,
    annotations: new Map(), // pageNum -> Array<{ tool, color, size, points: [[nx, ny], ...] }>
    activeStroke: null // { pageNum, tool, color, size, points }
  };

  const container = document.getElementById("pages-container");

  function notify(fn, ...args) {
    if (window.AndroidBridge && typeof window.AndroidBridge[fn] === "function") {
      try {
        window.AndroidBridge[fn](...args);
      } catch (e) {
        console.error("Bridge error:", fn, e);
      }
    }
  }

  function countTotalAnnotations() {
    let total = 0;
    for (const [, list] of state.annotations) {
      if (Array.isArray(list)) total += list.length;
    }
    return total;
  }

  function notifyAnnotationsCount() {
    notify("onAnnotationsChanged", countTotalAnnotations());
  }

  function computeScaleForViewport(pageViewport) {
    const paddingX = 16;
    const availW = Math.max(240, window.innerWidth - paddingX);
    const availH = Math.max(240, window.innerHeight - 100);
    if (state.fitMode === "width") {
      return (availW / pageViewport.width) * state.zoom;
    } else if (state.fitMode === "page") {
      const sW = availW / pageViewport.width;
      const sH = availH / pageViewport.height;
      return Math.min(sW, sH) * state.zoom;
    }
    return (availW / pageViewport.width) * state.zoom;
  }

  async function buildPagePlaceholders() {
    container.innerHTML = "";
    state.renderedPages.clear();
    state.pageDimensions.clear();

    const firstPage = await state.pdfDoc.getPage(1);
    const baseVp = firstPage.getViewport({ scale: 1.0, rotation: state.rotation });
    const defaultScale = computeScaleForViewport(baseVp);
    const defaultW = Math.round(baseVp.width * defaultScale);
    const defaultH = Math.round(baseVp.height * defaultScale);

    for (let p = 1; p <= state.pageCount; p++) {
      const wrapper = document.createElement("div");
      wrapper.className = "pdf-page-wrapper";
      wrapper.dataset.page = String(p);
      wrapper.id = "pdf-page-" + p;
      wrapper.style.width = defaultW + "px";
      wrapper.style.height = defaultH + "px";

      if (!state.continuous && p !== state.currentPage) {
        wrapper.classList.add("hidden-single");
      }

      const label = document.createElement("div");
      label.className = "page-placeholder-label";
      label.textContent = String(p);
      wrapper.appendChild(label);

      const canvas = document.createElement("canvas");
      canvas.className = "pdf-canvas";
      wrapper.appendChild(canvas);

      const textLayer = document.createElement("div");
      textLayer.className = "textLayer";
      wrapper.appendChild(textLayer);

      const highlightLayer = document.createElement("div");
      highlightLayer.className = "highlight-layer";
      wrapper.appendChild(highlightLayer);

      const annotCanvas = document.createElement("canvas");
      annotCanvas.className = "annotation-canvas";
      annotCanvas.dataset.page = String(p);
      attachAnnotationListeners(annotCanvas, p);
      wrapper.appendChild(annotCanvas);

      container.appendChild(wrapper);
    }

    setupIntersectionObserver();
  }

  function setupIntersectionObserver() {
    if (state.observer) {
      state.observer.disconnect();
    }
    state.observer = new IntersectionObserver(
      (entries) => {
        let bestPage = state.currentPage;
        let bestRatio = 0;

        entries.forEach((entry) => {
          const pageNum = parseInt(entry.target.dataset.page, 10);
          if (entry.isIntersecting) {
            renderPage(pageNum);
            if (entry.intersectionRatio > bestRatio) {
              bestRatio = entry.intersectionRatio;
              bestPage = pageNum;
            }
          }
        });

        if (state.continuous && bestRatio > 0.15 && bestPage !== state.currentPage) {
          state.currentPage = bestPage;
          notify("onPageChanged", state.currentPage, state.pageCount);
        }
      },
      {
        root: null,
        rootMargin: "400px 0px",
        threshold: [0.05, 0.25, 0.55, 0.85]
      }
    );

    const wrappers = container.querySelectorAll(".pdf-page-wrapper");
    wrappers.forEach((w) => state.observer.observe(w));
  }

  const MAX_CANVAS_PIXELS = 4500000;
  const MAX_CANVAS_DIM = 2800;

  function computeSafeDpr(cssWidth, cssHeight, visualScale) {
    const vvScale = Math.max(
      1.0,
      Math.min(3.0, visualScale || (window.visualViewport ? window.visualViewport.scale : 1.0) || 1.0)
    );
    const baseDpr = Math.min((window.devicePixelRatio || 1) * Math.min(vvScale, 2.25), 3.2);
    const w = Math.max(1, cssWidth);
    const h = Math.max(1, cssHeight);
    const dimLimit = Math.min(MAX_CANVAS_DIM / w, MAX_CANVAS_DIM / h);
    const areaLimit = Math.sqrt(MAX_CANVAS_PIXELS / (w * h));
    return Math.max(0.5, Math.min(baseDpr, dimLimit, areaLimit));
  }

  function hasAnnotationsOnPage(pageNum) {
    const list = state.annotations.get(pageNum);
    return Array.isArray(list) && list.length > 0;
  }

  function configureAnnotationCanvasSize(wrapper, pageNum, cssWidth, cssHeight) {
    const annotCanvas = wrapper.querySelector(".annotation-canvas");
    if (!annotCanvas) return;
    const needsBacking =
      state.annotationTool !== "none" ||
      hasAnnotationsOnPage(pageNum) ||
      (state.activeStroke && state.activeStroke.pageNum === pageNum);

    annotCanvas.style.width = Math.round(cssWidth) + "px";
    annotCanvas.style.height = Math.round(cssHeight) + "px";
    annotCanvas.style.display = needsBacking ? "block" : "none";

    if (!needsBacking) {
      if (annotCanvas.width !== 1 || annotCanvas.height !== 1) {
        annotCanvas.width = 1;
        annotCanvas.height = 1;
      }
      return;
    }

    const dpr = computeSafeDpr(cssWidth, cssHeight);
    annotCanvas.width = Math.max(1, Math.floor(cssWidth * dpr));
    annotCanvas.height = Math.max(1, Math.floor(cssHeight * dpr));
    redrawAnnotationsOnPage(pageNum, dpr);
  }

  function drawSingleStroke(ctx, cssWidth, cssHeight, stroke) {
    if (!stroke || !stroke.points || stroke.points.length === 0) return;
    const scaleRef = cssWidth / 600;
    const isHighlighter = stroke.tool === "highlighter";

    ctx.save();
    ctx.strokeStyle = stroke.color || "#E11D48";
    ctx.fillStyle = stroke.color || "#E11D48";
    ctx.lineJoin = "round";
    ctx.lineCap = isHighlighter ? "square" : "round";
    ctx.globalAlpha = isHighlighter ? 0.36 : 1.0;
    const baseSize = stroke.size || 3.5;
    ctx.lineWidth = Math.max(1.5, (isHighlighter ? baseSize * 4.2 : baseSize) * scaleRef);

    const pts = stroke.points;
    if (stroke.tool === "underline") {
      const first = pts[0];
      const last = pts[pts.length - 1];
      ctx.beginPath();
      ctx.moveTo(first[0] * cssWidth, first[1] * cssHeight);
      ctx.lineTo(last[0] * cssWidth, last[1] * cssHeight);
      ctx.stroke();
    } else if (stroke.tool === "rect") {
      const first = pts[0];
      const last = pts[pts.length - 1];
      const x = Math.min(first[0], last[0]) * cssWidth;
      const y = Math.min(first[1], last[1]) * cssHeight;
      const w = Math.abs(last[0] - first[0]) * cssWidth;
      const h = Math.abs(last[1] - first[1]) * cssHeight;
      ctx.strokeRect(x, y, w, h);
    } else {
      if (pts.length === 1) {
        ctx.beginPath();
        ctx.arc(pts[0][0] * cssWidth, pts[0][1] * cssHeight, ctx.lineWidth / 2, 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.beginPath();
        ctx.moveTo(pts[0][0] * cssWidth, pts[0][1] * cssHeight);
        for (let i = 1; i < pts.length; i++) {
          const prev = pts[i - 1];
          const cur = pts[i];
          const midX = ((prev[0] + cur[0]) * 0.5) * cssWidth;
          const midY = ((prev[1] + cur[1]) * 0.5) * cssHeight;
          ctx.quadraticCurveTo(prev[0] * cssWidth, prev[1] * cssHeight, midX, midY);
        }
        const last = pts[pts.length - 1];
        ctx.lineTo(last[0] * cssWidth, last[1] * cssHeight);
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  function redrawAnnotationsOnPage(pageNum, explicitDpr) {
    const wrapper = document.getElementById("pdf-page-" + pageNum);
    if (!wrapper) return;
    const annotCanvas = wrapper.querySelector(".annotation-canvas");
    if (!annotCanvas) return;

    const cssWidth = annotCanvas.clientWidth || parseFloat(wrapper.style.width) || 1;
    const cssHeight = annotCanvas.clientHeight || parseFloat(wrapper.style.height) || 1;
    const dpr = explicitDpr || computeSafeDpr(cssWidth, cssHeight);

    const targetW = Math.max(1, Math.floor(cssWidth * dpr));
    const targetH = Math.max(1, Math.floor(cssHeight * dpr));
    if (annotCanvas.width !== targetW || annotCanvas.height !== targetH) {
      annotCanvas.width = targetW;
      annotCanvas.height = targetH;
    }

    const ctx = annotCanvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, cssWidth, cssHeight);

    const list = state.annotations.get(pageNum) || [];
    for (let i = 0; i < list.length; i++) {
      drawSingleStroke(ctx, cssWidth, cssHeight, list[i]);
    }
    if (state.activeStroke && state.activeStroke.pageNum === pageNum) {
      drawSingleStroke(ctx, cssWidth, cssHeight, state.activeStroke);
    }
  }

  function eraseAtNormalizedPoint(pageNum, nx, ny) {
    const list = state.annotations.get(pageNum);
    if (!list || list.length === 0) return;
    const threshold = 0.04;
    const filtered = list.filter((stroke) => {
      if (!stroke.points) return true;
      for (let i = 0; i < stroke.points.length; i++) {
        const dx = stroke.points[i][0] - nx;
        const dy = stroke.points[i][1] - ny;
        if (Math.hypot(dx, dy) <= threshold) {
          return false;
        }
      }
      return true;
    });
    if (filtered.length !== list.length) {
      state.annotations.set(pageNum, filtered);
      redrawAnnotationsOnPage(pageNum);
      notifyAnnotationsCount();
    }
  }

  function attachAnnotationListeners(annotCanvas, pageNum) {
    function getNormalizedCoords(evt) {
      const rect = annotCanvas.getBoundingClientRect();
      const clientX = evt.touches && evt.touches.length > 0 ? evt.touches[0].clientX : evt.clientX;
      const clientY = evt.touches && evt.touches.length > 0 ? evt.touches[0].clientY : evt.clientY;
      const nx = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
      const ny = Math.max(0, Math.min(1, (clientY - rect.top) / Math.max(1, rect.height)));
      return [Number(nx.toFixed(4)), Number(ny.toFixed(4))];
    }

    annotCanvas.addEventListener("pointerdown", (e) => {
      if (state.annotationTool === "none") return;
      if (!e.isPrimary) {
        if (state.activeStroke && state.activeStroke.pageNum === pageNum) {
          state.activeStroke = null;
          redrawAnnotationsOnPage(pageNum);
        }
        return;
      }
      e.preventDefault();
      e.stopPropagation();
      try {
        annotCanvas.setPointerCapture(e.pointerId);
      } catch (_) {}

      state.currentPage = pageNum;
      const pt = getNormalizedCoords(e);

      if (state.annotationTool === "eraser") {
        state.activeStroke = { pageNum, tool: "eraser" };
        eraseAtNormalizedPoint(pageNum, pt[0], pt[1]);
        return;
      }

      state.activeStroke = {
        pageNum,
        tool: state.annotationTool,
        color: state.annotationColor,
        size: state.annotationSize,
        points: [pt]
      };
      redrawAnnotationsOnPage(pageNum);
    });

    annotCanvas.addEventListener("pointermove", (e) => {
      if (!state.activeStroke || state.activeStroke.pageNum !== pageNum) return;
      if (!e.isPrimary) return;
      e.preventDefault();
      e.stopPropagation();

      const pt = getNormalizedCoords(e);
      if (state.activeStroke.tool === "eraser") {
        eraseAtNormalizedPoint(pageNum, pt[0], pt[1]);
        return;
      }

      if (state.activeStroke.tool === "underline" || state.activeStroke.tool === "rect") {
        state.activeStroke.points = [state.activeStroke.points[0], pt];
      } else {
        const pts = state.activeStroke.points;
        const last = pts[pts.length - 1];
        if (!last || Math.hypot(pt[0] - last[0], pt[1] - last[1]) > 0.0025) {
          pts.push(pt);
        }
      }
      redrawAnnotationsOnPage(pageNum);
    });

    function finishStroke(e) {
      if (!state.activeStroke || state.activeStroke.pageNum !== pageNum) return;
      e.preventDefault();
      e.stopPropagation();

      const finished = state.activeStroke;
      state.activeStroke = null;
      if (finished.tool !== "eraser" && finished.points && finished.points.length > 0) {
        if (!state.annotations.has(pageNum)) {
          state.annotations.set(pageNum, []);
        }
        state.annotations.get(pageNum).push({
          tool: finished.tool,
          color: finished.color,
          size: finished.size,
          points: finished.points
        });
        redrawAnnotationsOnPage(pageNum);
        notifyAnnotationsCount();
      }
    }

    annotCanvas.addEventListener("pointerup", finishStroke);
    annotCanvas.addEventListener("pointercancel", finishStroke);
  }

  async function renderPage(pageNum) {
    if (!state.pdfDoc || pageNum < 1 || pageNum > state.pageCount) return;
    if (!state.continuous && pageNum !== state.currentPage) return;

    const wrapper = document.getElementById("pdf-page-" + pageNum);
    if (!wrapper) return;

    const cached = state.renderedPages.get(pageNum);
    const targetKey = `${state.zoom.toFixed(2)}_${state.rotation}_${state.fitMode}`;
    if (cached && cached.key === targetKey) return;
    if (state.renderQueue.has(pageNum)) return;

    state.renderQueue.add(pageNum);
    const token = state.renderToken;

    try {
      const page = await state.pdfDoc.getPage(pageNum);
      if (token !== state.renderToken) return;

      const unitVp = page.getViewport({ scale: 1.0, rotation: state.rotation });
      const cssScale = computeScaleForViewport(unitVp);
      const viewport = page.getViewport({ scale: cssScale, rotation: state.rotation });
      const dpr = computeSafeDpr(viewport.width, viewport.height);

      wrapper.style.width = Math.round(viewport.width) + "px";
      wrapper.style.height = Math.round(viewport.height) + "px";

      const canvas = wrapper.querySelector(".pdf-canvas");
      const ctx = canvas.getContext("2d", { alpha: false });
      canvas.width = Math.max(1, Math.floor(viewport.width * dpr));
      canvas.height = Math.max(1, Math.floor(viewport.height * dpr));
      canvas.style.width = Math.round(viewport.width) + "px";
      canvas.style.height = Math.round(viewport.height) + "px";

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(0, 0, viewport.width, viewport.height);

      await page.render({
        canvasContext: ctx,
        viewport: viewport
      }).promise;

      if (token !== state.renderToken) return;

      const label = wrapper.querySelector(".page-placeholder-label");
      if (label) label.style.display = "none";

      // Text layer for selection & search
      const textLayerDiv = wrapper.querySelector(".textLayer");
      textLayerDiv.innerHTML = "";
      textLayerDiv.style.width = Math.round(viewport.width) + "px";
      textLayerDiv.style.height = Math.round(viewport.height) + "px";
      textLayerDiv.style.setProperty("--scale-factor", String(cssScale));
      const textContent = await page.getTextContent({ includeMarkedContent: true });

      const extractedFromItems = buildTextLinesFromItems(textContent.items || []);
      if (extractedFromItems) {
        state.pageTexts.set(pageNum, extractedFromItems);
      }

      if (typeof pdfjsLib.renderTextLayer === "function") {
        try {
          const textDivs = [];
          const task = pdfjsLib.renderTextLayer({
            textContentSource: textContent,
            textContent: textContent,
            container: textLayerDiv,
            viewport: viewport,
            textDivs: textDivs
          });
          if (task && task.promise) {
            await task.promise;
          }
        } catch (_) {}
      }

      const renderedSpans = textLayerDiv.querySelectorAll("span:not(.markedContent)");
      if (renderedSpans.length === 0 && textContent.items && textContent.items.length > 0) {
        textContent.items.forEach((item) => {
          if (!item.str) return;
          const tx = pdfjsLib.Util.transform(viewport.transform, item.transform);
          const fontHeight = Math.hypot(tx[2], tx[3]);
          const span = document.createElement("span");
          span.textContent = item.str;
          span.style.left = Math.round(tx[4]) + "px";
          span.style.top = Math.round(tx[5] - fontHeight) + "px";
          span.style.fontSize = Math.max(8, Math.round(fontHeight)) + "px";
          span.style.fontFamily = "sans-serif";
          textLayerDiv.appendChild(span);
        });
      }

      state.renderedPages.set(pageNum, { key: targetKey, scale: cssScale, dpr, viewport });
      renderHighlightsForPage(pageNum, viewport);
      configureAnnotationCanvasSize(wrapper, pageNum, viewport.width, viewport.height);

      // Evict far-away canvases to keep memory bounded on huge PDFs
      evictDistantPages(pageNum);
    } catch (err) {
      console.error("Render error page", pageNum, err);
    } finally {
      state.renderQueue.delete(pageNum);
    }
  }

  function evictDistantPages(centerPage) {
    if (state.pageCount <= 6) return;
    for (const [p] of state.renderedPages) {
      if (Math.abs(p - centerPage) > 4) {
        const w = document.getElementById("pdf-page-" + p);
        if (w) {
          const canvas = w.querySelector(".pdf-canvas");
          if (canvas) {
            canvas.width = 0;
            canvas.height = 0;
          }
          const annotCanvas = w.querySelector(".annotation-canvas");
          if (annotCanvas) {
            annotCanvas.width = 1;
            annotCanvas.height = 1;
          }
          const tl = w.querySelector(".textLayer");
          if (tl) tl.innerHTML = "";
          const label = w.querySelector(".page-placeholder-label");
          if (label) label.style.display = "block";
        }
        state.renderedPages.delete(p);
      }
    }
  }

  function renderHighlightsForPage(pageNum, viewport) {
    const wrapper = document.getElementById("pdf-page-" + pageNum);
    if (!wrapper) return;
    const hlLayer = wrapper.querySelector(".highlight-layer");
    hlLayer.innerHTML = "";
    if (!state.searchQuery || state.matches.length === 0) return;

    state.matches.forEach((m, idx) => {
      if (m.pageNum !== pageNum) return;
      const vpRect = viewport.convertToViewportRectangle([
        m.pdfX,
        m.pdfY,
        m.pdfX + m.pdfW,
        m.pdfY + m.pdfH
      ]);
      const left = Math.min(vpRect[0], vpRect[2]);
      const top = Math.min(vpRect[1], vpRect[3]);
      const width = Math.max(6, Math.abs(vpRect[2] - vpRect[0]));
      const height = Math.max(8, Math.abs(vpRect[3] - vpRect[1]));

      const box = document.createElement("div");
      box.className = "search-match-box" + (idx === state.activeMatchIdx ? " active" : "");
      box.style.left = left + "px";
      box.style.top = top + "px";
      box.style.width = width + "px";
      box.style.height = height + "px";
      hlLayer.appendChild(box);
    });
  }

  function refreshVisibleHighlights() {
    for (const [pageNum, info] of state.renderedPages) {
      renderHighlightsForPage(pageNum, info.viewport);
    }
  }

  async function extractOutline() {
    try {
      const outline = await state.pdfDoc.getOutline();
      if (!outline || outline.length === 0) {
        notify("onOutlineLoaded", "[]");
        return;
      }
      const result = [];
      async function walk(items, depth) {
        for (const item of items) {
          let pageNum = 1;
          try {
            if (typeof item.dest === "string") {
              const dest = await state.pdfDoc.getDestination(item.dest);
              if (dest && dest[0]) {
                pageNum = (await state.pdfDoc.getPageIndex(dest[0])) + 1;
              }
            } else if (Array.isArray(item.dest) && item.dest[0]) {
              pageNum = (await state.pdfDoc.getPageIndex(item.dest[0])) + 1;
            }
          } catch (_) {}
          result.push({ title: item.title || "Section", page: pageNum, depth });
          if (item.items && item.items.length > 0 && result.length < 150) {
            await walk(item.items, depth + 1);
          }
        }
      }
      await walk(outline, 0);
      notify("onOutlineLoaded", JSON.stringify(result));
    } catch (e) {
      notify("onOutlineLoaded", "[]");
    }
  }

  async function loadPdf(docUrl, initialPage, continuous, nightMode) {
    try {
      state.currentPage = Math.max(1, initialPage || 1);
      state.continuous = continuous !== false;
      setNightMode(!!nightMode);

      const loadingTask = pdfjsLib.getDocument({
        url: docUrl,
        cMapUrl: "./cmaps/",
        cMapPacked: true,
        standardFontDataUrl: "./standard_fonts/",
        disableAutoFetch: false,
        disableStream: false
      });

      state.pdfDoc = await loadingTask.promise;
      state.pageCount = state.pdfDoc.numPages;
      state.currentPage = Math.min(state.currentPage, state.pageCount);

      await buildPagePlaceholders();
      notify("onDocumentLoaded", state.pageCount);

      goToPage(state.currentPage);
      await renderPage(state.currentPage);
      if (state.currentPage + 1 <= state.pageCount) {
        renderPage(state.currentPage + 1);
      }
      extractOutline();
    } catch (err) {
      notify("onDocumentError", err && err.message ? err.message : String(err));
    }
  }

  function goToPage(pageNum) {
    if (!state.pdfDoc) return;
    const target = Math.max(1, Math.min(state.pageCount, parseInt(pageNum, 10) || 1));
    state.currentPage = target;

    if (!state.continuous) {
      const wrappers = container.querySelectorAll(".pdf-page-wrapper");
      wrappers.forEach((w) => {
        const p = parseInt(w.dataset.page, 10);
        w.classList.toggle("hidden-single", p !== target);
      });
      window.scrollTo({ top: 0, behavior: "instant" });
      renderPage(target);
    } else {
      const el = document.getElementById("pdf-page-" + target);
      if (el) {
        el.scrollIntoView({ behavior: "smooth", block: "start" });
      }
      renderPage(target);
    }
    notify("onPageChanged", state.currentPage, state.pageCount);
  }

  function setZoom(zoomFactor) {
    const clamped = Math.max(0.5, Math.min(4.0, parseFloat(zoomFactor) || 1.0));
    state.zoom = clamped;
    state.renderToken++;
    state.renderedPages.clear();
    const current = state.currentPage;
    renderPage(current);
    if (current + 1 <= state.pageCount) renderPage(current + 1);
    if (current - 1 >= 1) renderPage(current - 1);
    notify("onZoomChanged", state.zoom);
  }

  function rotateClockwise() {
    state.rotation = (state.rotation + 90) % 360;
    state.renderToken++;
    state.renderedPages.clear();
    const cur = state.currentPage;
    buildPagePlaceholders().then(() => goToPage(cur));
  }

  function setNightMode(enabled) {
    state.nightMode = !!enabled;
    document.body.classList.toggle("night-mode", state.nightMode);
  }

  function setContinuousMode(enabled) {
    state.continuous = !!enabled;
    const wrappers = container.querySelectorAll(".pdf-page-wrapper");
    wrappers.forEach((w) => {
      const p = parseInt(w.dataset.page, 10);
      w.classList.toggle("hidden-single", !state.continuous && p !== state.currentPage);
    });
    goToPage(state.currentPage);
  }

  // Annotation API methods
  function setAnnotationTool(tool, colorHex, strokeSize) {
    state.annotationTool = tool || "none";
    if (colorHex) state.annotationColor = colorHex;
    if (strokeSize) state.annotationSize = parseFloat(strokeSize) || 3.5;
    document.body.classList.toggle("annotating-active", state.annotationTool !== "none");
    for (const [pageNum, info] of state.renderedPages) {
      const wrapper = document.getElementById("pdf-page-" + pageNum);
      if (wrapper && info && info.viewport) {
        configureAnnotationCanvasSize(wrapper, pageNum, info.viewport.width, info.viewport.height);
      }
    }
  }

  function undoAnnotation() {
    const curPage = state.currentPage || 1;
    const curList = state.annotations.get(curPage);
    if (curList && curList.length > 0) {
      curList.pop();
      redrawAnnotationsOnPage(curPage);
      notifyAnnotationsCount();
      return;
    }
    // Otherwise undo from the most recently annotated page
    for (const [p, list] of state.annotations) {
      if (list && list.length > 0) {
        list.pop();
        redrawAnnotationsOnPage(p);
        notifyAnnotationsCount();
        return;
      }
    }
  }

  function clearPageAnnotations() {
    const curPage = state.currentPage || 1;
    if (state.annotations.has(curPage)) {
      state.annotations.set(curPage, []);
      redrawAnnotationsOnPage(curPage);
      notifyAnnotationsCount();
    }
  }

  function clearAllAnnotations() {
    state.annotations.clear();
    for (let p = 1; p <= state.pageCount; p++) {
      redrawAnnotationsOnPage(p);
    }
    notifyAnnotationsCount();
  }

  function requestSaveAnnotations() {
    const serialized = {};
    for (const [pageNum, strokes] of state.annotations) {
      if (Array.isArray(strokes) && strokes.length > 0) {
        serialized[String(pageNum)] = strokes;
      }
    }
    notify("onAnnotationsSaved", JSON.stringify(serialized));
  }

  async function search(query) {
    state.searchQuery = (query || "").trim();
    state.matches = [];
    state.activeMatchIdx = -1;

    if (!state.pdfDoc || !state.searchQuery) {
      refreshVisibleHighlights();
      notify("onSearchResult", 0, 0, state.currentPage);
      return;
    }

    const needle = state.searchQuery.toLowerCase();
    for (let p = 1; p <= state.pageCount; p++) {
      const page = await state.pdfDoc.getPage(p);
      const tc = await page.getTextContent();
      for (let i = 0; i < tc.items.length; i++) {
        const item = tc.items[i];
        const str = item.str || "";
        const lower = str.toLowerCase();
        let idx = lower.indexOf(needle);
        while (idx !== -1) {
          const tx = item.transform;
          const totalLen = Math.max(1, str.length);
          const charWidth = (item.width || 10) / totalLen;
          const pdfX = tx[4] + idx * charWidth;
          const pdfW = Math.max(8, needle.length * charWidth);
          const pdfH = Math.max(10, Math.abs(tx[3]) || item.height || 12);
          const pdfY = tx[5];
          state.matches.push({ pageNum: p, pdfX, pdfY, pdfW, pdfH });
          idx = lower.indexOf(needle, idx + needle.length);
        }
      }
    }

    if (state.matches.length > 0) {
      state.activeMatchIdx = 0;
      const firstMatch = state.matches[0];
      goToPage(firstMatch.pageNum);
      await renderPage(firstMatch.pageNum);
    }
    refreshVisibleHighlights();
    notify(
      "onSearchResult",
      state.matches.length > 0 ? state.activeMatchIdx + 1 : 0,
      state.matches.length,
      state.matches.length > 0 ? state.matches[state.activeMatchIdx].pageNum : state.currentPage
    );
  }

  function stepSearch(delta) {
    if (state.matches.length === 0) return;
    state.activeMatchIdx = (state.activeMatchIdx + delta + state.matches.length) % state.matches.length;
    const match = state.matches[state.activeMatchIdx];
    goToPage(match.pageNum);
    renderPage(match.pageNum).then(() => refreshVisibleHighlights());
    notify("onSearchResult", state.activeMatchIdx + 1, state.matches.length, match.pageNum);
  }

  async function renderThumbnails(startPage, endPage) {
    if (!state.pdfDoc) return;
    const s = Math.max(1, startPage);
    const e = Math.min(state.pageCount, endPage);
    for (let p = s; p <= e; p++) {
      const canvas = document.createElement("canvas");
      try {
        const page = await state.pdfDoc.getPage(p);
        const unitVp = page.getViewport({ scale: 1.0, rotation: state.rotation });
        const thumbScale = Math.min(0.22, 220 / Math.max(1, unitVp.width));
        const vp = page.getViewport({ scale: thumbScale, rotation: state.rotation });
        canvas.width = Math.max(1, Math.floor(vp.width));
        canvas.height = Math.max(1, Math.floor(vp.height));
        const ctx = canvas.getContext("2d", { alpha: false });
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        await page.render({ canvasContext: ctx, viewport: vp }).promise;
        const dataUrl = canvas.toDataURL("image/jpeg", 0.72);
        notify("onThumbnailReady", p, dataUrl);
      } catch (_) {
      } finally {
        canvas.width = 0;
        canvas.height = 0;
      }
      await new Promise((resolve) => setTimeout(resolve, 16));
    }
  }

  function buildTextLinesFromItems(items) {
    if (!Array.isArray(items) || items.length === 0) return "";
    const lines = [];
    let lastY = null;
    let lineBuf = "";
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (!it || typeof it.str !== "string") continue;
      const s = it.str;
      if (!s && !it.hasEOL) continue;
      const y = it.transform ? Math.round(it.transform[5]) : lastY;
      if (lastY !== null && y !== null && Math.abs(y - lastY) > 4) {
        if (lineBuf.trim()) lines.push(lineBuf.trim());
        lineBuf = s;
      } else {
        if (s) {
          lineBuf += (lineBuf && !lineBuf.endsWith(" ") && !s.startsWith(" ") ? " " : "") + s;
        }
      }
      if (it.hasEOL) {
        if (lineBuf.trim()) lines.push(lineBuf.trim());
        lineBuf = "";
        lastY = null;
      } else {
        lastY = y;
      }
    }
    if (lineBuf.trim()) lines.push(lineBuf.trim());
    return lines.join("\n").trim();
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
    if (state.annotationTool !== "none") return;
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

  async function extractSinglePageInternal(pageNum) {
    const cached = state.pageTexts.get(pageNum);
    if (cached && cached.trim()) return cached.trim();

    const page = await state.pdfDoc.getPage(pageNum);
    const tc = await page.getTextContent({ includeMarkedContent: true });
    let text = buildTextLinesFromItems(tc.items || []);

    if (!text) {
      const wrapper = document.getElementById("pdf-page-" + pageNum);
      const tl = wrapper ? wrapper.querySelector(".textLayer") : null;
      if (tl) {
        text = (tl.innerText || tl.textContent || "").trim();
      }
    }
    if (text) {
      state.pageTexts.set(pageNum, text);
    }
    return text;
  }

  async function extractPageText(explicitPage) {
    if (!state.pdfDoc) {
      notify("onPageTextExtracted", "__DOCUVIEW_NO_TEXT_LAYER__");
      return "";
    }
    try {
      const reqPage = parseInt(explicitPage, 10);
      const targetPage = Math.max(1, Math.min(state.pageCount, reqPage > 0 ? reqPage : (state.currentPage || 1)));
      const text = await extractSinglePageInternal(targetPage);
      notify("onPageTextExtracted", text && text.trim() ? text.trim() : "__DOCUVIEW_NO_TEXT_LAYER__");
      return text || "";
    } catch (e) {
      notify("onPageTextExtracted", "__DOCUVIEW_NO_TEXT_LAYER__");
      return "";
    }
  }

  async function extractAllPagesText() {
    if (!state.pdfDoc) {
      notify("onPageTextExtracted", "__DOCUVIEW_NO_TEXT_LAYER__");
      return "";
    }
    try {
      const parts = [];
      for (let p = 1; p <= state.pageCount; p++) {
        const pageText = await extractSinglePageInternal(p);
        if (pageText && pageText.trim()) {
          parts.push(`--- #${p} ---\n${pageText.trim()}`);
        }
      }
      const combined = parts.join("\n\n").trim();
      notify("onPageTextExtracted", combined ? combined : "__DOCUVIEW_NO_TEXT_LAYER__");
      return combined;
    } catch (e) {
      notify("onPageTextExtracted", "__DOCUVIEW_NO_TEXT_LAYER__");
      return "";
    }
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

  async function refineVisiblePagesForVisualZoom() {
    if (!state.pdfDoc || !window.visualViewport) return;
    const vvScale = window.visualViewport.scale || 1.0;
    const token = state.renderToken;
    for (const [pageNum, info] of Array.from(state.renderedPages.entries())) {
      if (token !== state.renderToken) return;
      if (state.renderQueue.has(pageNum)) continue;
      const wrapper = document.getElementById("pdf-page-" + pageNum);
      if (!wrapper || wrapper.classList.contains("hidden-single")) continue;
      const rect = wrapper.getBoundingClientRect();
      if (rect.bottom < -300 || rect.top > window.innerHeight + 300) continue;

      const targetDpr = computeSafeDpr(info.viewport.width, info.viewport.height, vvScale);
      if (Math.abs(targetDpr - (info.dpr || 1)) < 0.3) continue;

      const canvas = wrapper.querySelector(".pdf-canvas");
      if (!canvas) continue;
      const offscreen = document.createElement("canvas");
      try {
        state.renderQueue.add(pageNum);
        const page = await state.pdfDoc.getPage(pageNum);
        if (token !== state.renderToken) continue;

        offscreen.width = Math.max(1, Math.floor(info.viewport.width * targetDpr));
        offscreen.height = Math.max(1, Math.floor(info.viewport.height * targetDpr));
        const offCtx = offscreen.getContext("2d", { alpha: false });
        offCtx.setTransform(targetDpr, 0, 0, targetDpr, 0, 0);
        offCtx.fillStyle = "#ffffff";
        offCtx.fillRect(0, 0, info.viewport.width, info.viewport.height);

        await page.render({ canvasContext: offCtx, viewport: info.viewport }).promise;
        if (token !== state.renderToken || !state.renderedPages.has(pageNum)) continue;

        canvas.width = offscreen.width;
        canvas.height = offscreen.height;
        const ctx = canvas.getContext("2d", { alpha: false });
        ctx.drawImage(offscreen, 0, 0);
        info.dpr = targetDpr;
        configureAnnotationCanvasSize(wrapper, pageNum, info.viewport.width, info.viewport.height);
      } catch (_) {
      } finally {
        offscreen.width = 0;
        offscreen.height = 0;
        state.renderQueue.delete(pageNum);
      }
    }
  }

  function scheduleVisualZoomRefinement() {
    if (state.visualRefineTimer) {
      clearTimeout(state.visualRefineTimer);
    }
    state.visualRefineTimer = setTimeout(() => {
      refineVisiblePagesForVisualZoom();
    }, 220);
  }

  if (window.visualViewport) {
    window.visualViewport.addEventListener("resize", scheduleVisualZoomRefinement, { passive: true });
  }

  window.DocuPdf = {
    load: loadPdf,
    goToPage,
    setZoom,
    rotateClockwise,
    setNightMode,
    setContinuousMode,
    search,
    findNext: () => stepSearch(1),
    findPrev: () => stepSearch(-1),
    renderThumbnails,
    getSelectedText,
    extractPageText,
    extractAllPagesText,
    setAnnotationTool,
    undoAnnotation,
    clearPageAnnotations,
    clearAllAnnotations,
    requestSaveAnnotations
  };

  // Auto-init from query parameters if provided
  const params = new URLSearchParams(window.location.search);
  const docParam = params.get("doc");
  if (docParam) {
    loadPdf(
      docParam,
      parseInt(params.get("page") || "1", 10),
      params.get("continuous") !== "0",
      params.get("night") === "1"
    );
  }
})();
