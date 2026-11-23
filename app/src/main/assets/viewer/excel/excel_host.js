(function () {
  "use strict";

  const PAGE_ROW_CHUNK = 500;
  const MAX_RENDER_COLS = 60;

  const state = {
    workbook: null,
    sheetNames: [],
    activeSheetIdx: 0, // 0-based
    visibleRowLimit: PAGE_ROW_CHUNK,
    zoom: 1.0,
    nightMode: false,
    dirMode: "auto",
    selectedCellText: "",
    matches: [],
    activeMatchIdx: -1
  };

  const tabsEl = document.getElementById("sheet-tabs");
  const containerEl = document.getElementById("sheet-table-container");
  const scaleHostEl = document.getElementById("sheet-scale-host");
  const moreBarEl = document.getElementById("sheet-more-bar");
  const cellRefBadge = document.getElementById("cell-ref-badge");
  const cellPreviewEl = document.getElementById("cell-value-preview");
  const copyCellBtn = document.getElementById("copy-cell-btn");

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
    scaleHostEl.style.transform = "none";
    scaleHostEl.style.zoom = String(state.zoom);
    scaleHostEl.style.width = "max-content";
    scaleHostEl.style.minWidth = "100%";
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
    if (state.dirMode === "auto") {
      const sheetRtl = isCurrentSheetRtl();
      document.body.classList.add(sheetRtl ? "dir-rtl" : "dir-ltr");
    } else {
      document.body.classList.add("dir-" + state.dirMode);
    }
  }

  function isCurrentSheetRtl() {
    try {
      const wb = state.workbook;
      if (wb && wb.Workbook && Array.isArray(wb.Workbook.Views)) {
        const view = wb.Workbook.Views[0];
        if (view && typeof view.RTL === "boolean") {
          return view.RTL;
        }
      }
      const sheetName = state.sheetNames[state.activeSheetIdx];
      const sheet = wb && wb.Sheets && wb.Sheets[sheetName];
      if (sheet && Array.isArray(sheet["!views"]) && sheet["!views"][0] && sheet["!views"][0].rightToLeft) {
        return true;
      }
      if (containerEl && /[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]/.test(containerEl.textContent || "")) {
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  function colLabel(idx) {
    return XLSX.utils.encode_col(idx);
  }

  const measureCanvas = document.createElement("canvas");
  const measureCtx = measureCanvas.getContext("2d");

  function measureCellTextWidthPx(text, isHeader) {
    if (!text) return 0;
    if (measureCtx) {
      measureCtx.font = (isHeader ? "700 " : "600 ") +
        '13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans Arabic", sans-serif';
    }
    const lines = String(text).split(/\r?\n/);
    let maxW = 0;
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (!line) continue;
      const w = measureCtx ? measureCtx.measureText(line).width : line.length * 8.5;
      if (w > maxW) maxW = w;
    }
    return maxW;
  }

  function computeAutoFitColumnWidths(sheet, startRow, renderRows, startCol, endCol, spanMap, skipSet) {
    const MIN_COL_PX = 48;
    const MAX_COL_PX = 340;
    const CELL_H_PADDING_PX = 28;
    const colCount = Math.max(0, endCol - startCol + 1);
    const colWidths = new Array(colCount).fill(MIN_COL_PX);
    const excelColsMeta = Array.isArray(sheet["!cols"]) ? sheet["!cols"] : [];

    for (let c = startCol; c <= endCol; c++) {
      const idx = c - startCol;
      const headerWidth = Math.ceil(measureCellTextWidthPx(colLabel(c), true)) + 20;
      let widthPx = Math.max(MIN_COL_PX, headerWidth);
      const meta = excelColsMeta[c];
      if (meta && !meta.hidden) {
        let metaPx = 0;
        if (typeof meta.wpx === "number" && meta.wpx > 0) {
          metaPx = meta.wpx + 12;
        } else if (typeof meta.wch === "number" && meta.wch > 0) {
          metaPx = meta.wch * 8 + 18;
        }
        if (metaPx > widthPx) {
          widthPx = Math.min(MAX_COL_PX, Math.ceil(metaPx));
        }
      }
      colWidths[idx] = widthPx;
    }

    const mergedHorizontalCells = [];
    for (let r = startRow; r <= renderRows; r++) {
      const isTopRow = r === startRow;
      for (let c = startCol; c <= endCol; c++) {
        const key = `${r},${c}`;
        if (skipSet.has(key)) continue;
        const addr = XLSX.utils.encode_cell({ r, c });
        const cell = sheet[addr];
        if (!cell) continue;
        const text = cell.w != null ? String(cell.w) : (cell.v != null ? String(cell.v) : "");
        if (!text) continue;

        const span = spanMap.get(key);
        const colspan = span ? Math.min(span.colspan, endCol - c + 1) : 1;
        const measuredW = Math.ceil(measureCellTextWidthPx(text, isTopRow || colspan > 1) * 1.06);
        const neededPx = measuredW + CELL_H_PADDING_PX;

        if (colspan > 1) {
          mergedHorizontalCells.push({ startIdx: c - startCol, colspan, neededPx });
        } else {
          const idx = c - startCol;
          const clamped = Math.min(MAX_COL_PX, Math.max(MIN_COL_PX, neededPx));
          if (clamped > colWidths[idx]) {
            colWidths[idx] = clamped;
          }
        }
      }
    }

    // Ensure horizontally merged cells (e.g., category group titles) never get clipped if spanned columns are narrow
    for (let i = 0; i < mergedHorizontalCells.length; i++) {
      const m = mergedHorizontalCells[i];
      let currentSum = 0;
      for (let k = 0; k < m.colspan; k++) {
        currentSum += colWidths[m.startIdx + k];
      }
      const targetSum = Math.min(MAX_COL_PX * m.colspan, m.neededPx);
      if (targetSum > currentSum) {
        const extraEach = Math.ceil((targetSum - currentSum) / m.colspan);
        for (let k = 0; k < m.colspan; k++) {
          colWidths[m.startIdx + k] = Math.min(MAX_COL_PX, colWidths[m.startIdx + k] + extraEach);
        }
      }
    }

    return colWidths;
  }

  function buildMergeLookup(sheet) {
    const merges = Array.isArray(sheet["!merges"]) ? sheet["!merges"] : [];
    const spanMap = new Map();
    const skipSet = new Set();
    for (const m of merges) {
      if (!m || !m.s || !m.e) continue;
      const key = `${m.s.r},${m.s.c}`;
      const rowspan = Math.max(1, m.e.r - m.s.r + 1);
      const colspan = Math.max(1, m.e.c - m.s.c + 1);
      spanMap.set(key, { rowspan, colspan });
      for (let r = m.s.r; r <= m.e.r; r++) {
        for (let c = m.s.c; c <= m.e.c; c++) {
          if (r === m.s.r && c === m.s.c) continue;
          skipSet.add(`${r},${c}`);
        }
      }
    }
    return { spanMap, skipSet };
  }

  function renderTabs() {
    tabsEl.innerHTML = "";
    state.sheetNames.forEach((name, idx) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "sheet-tab-btn" + (idx === state.activeSheetIdx ? " active" : "");
      const sheet = state.workbook.Sheets[name];
      let rowsCount = 0;
      if (sheet && sheet["!ref"]) {
        const range = XLSX.utils.decode_range(sheet["!ref"]);
        rowsCount = Math.max(0, range.e.r - range.s.r + 1);
      }
      const labelSpan = document.createElement("span");
      labelSpan.textContent = name;
      btn.appendChild(labelSpan);

      const metaSpan = document.createElement("span");
      metaSpan.className = "sheet-tab-meta";
      metaSpan.textContent = `(${rowsCount})`;
      btn.appendChild(metaSpan);

      btn.addEventListener("click", () => {
        goToPage(idx + 1);
      });
      tabsEl.appendChild(btn);
    });
  }

  function renderActiveSheet() {
    clearHighlights();
    containerEl.innerHTML = "";
    moreBarEl.innerHTML = "";
    moreBarEl.classList.add("hidden");

    const sheetName = state.sheetNames[state.activeSheetIdx];
    const sheet = state.workbook && state.workbook.Sheets[sheetName];
    if (!sheet || !sheet["!ref"]) {
      const empty = document.createElement("div");
      empty.className = "empty-sheet-card";
      empty.textContent = `Sheet "${sheetName || ""}" is empty`;
      containerEl.appendChild(empty);
      return;
    }

    const range = XLSX.utils.decode_range(sheet["!ref"]);
    const startRow = range.s.r;
    const totalRows = range.e.r - range.s.r + 1;
    const renderRows = Math.min(range.e.r, startRow + state.visibleRowLimit - 1);
    const startCol = range.s.c;
    const endCol = Math.min(range.e.c, startCol + MAX_RENDER_COLS - 1);

    const { spanMap, skipSet } = buildMergeLookup(sheet);
    const colWidths = computeAutoFitColumnWidths(sheet, startRow, renderRows, startCol, endCol, spanMap, skipSet);
    const ROW_NUM_COL_PX = 44;
    let totalTableWidthPx = ROW_NUM_COL_PX;
    for (let i = 0; i < colWidths.length; i++) {
      totalTableWidthPx += colWidths[i];
    }

    const table = document.createElement("table");
    table.className = "xlsx-grid";
    table.style.width = totalTableWidthPx + "px";
    table.style.minWidth = "100%";

    const colgroup = document.createElement("colgroup");
    const rowNumCol = document.createElement("col");
    rowNumCol.style.width = ROW_NUM_COL_PX + "px";
    rowNumCol.style.minWidth = ROW_NUM_COL_PX + "px";
    colgroup.appendChild(rowNumCol);
    for (let i = 0; i < colWidths.length; i++) {
      const colEl = document.createElement("col");
      colEl.style.width = colWidths[i] + "px";
      colEl.style.minWidth = colWidths[i] + "px";
      colgroup.appendChild(colEl);
    }
    table.appendChild(colgroup);

    // Column headers (A, B, C...)
    const thead = document.createElement("thead");
    const headRow = document.createElement("tr");
    const corner = document.createElement("th");
    corner.className = "corner-cell";
    corner.textContent = "#";
    headRow.appendChild(corner);

    for (let c = startCol; c <= endCol; c++) {
      const th = document.createElement("th");
      th.textContent = colLabel(c);
      headRow.appendChild(th);
    }
    thead.appendChild(headRow);
    table.appendChild(thead);

    const tbody = document.createElement("tbody");
    let firstCellSelected = false;

    for (let r = startRow; r <= renderRows; r++) {
      const tr = document.createElement("tr");
      const rowTh = document.createElement("th");
      rowTh.className = "row-num";
      rowTh.textContent = String(r + 1);
      tr.appendChild(rowTh);

      for (let c = startCol; c <= endCol; c++) {
        const key = `${r},${c}`;
        if (skipSet.has(key)) continue;

        const td = document.createElement("td");
        const span = spanMap.get(key);
        if (span) {
          if (span.rowspan > 1) td.rowSpan = span.rowspan;
          if (span.colspan > 1) {
            td.colSpan = span.colspan;
            td.classList.add("merged-cell");
          }
        }

        const addr = XLSX.utils.encode_cell({ r, c });
        const cell = sheet[addr];
        let displayText = "";
        let previewText = "";
        if (cell) {
          displayText = cell.w != null ? String(cell.w) : (cell.v != null ? String(cell.v) : "");
          if (cell.f) {
            previewText = `=${cell.f}   (${displayText})`;
            td.classList.add("formula-cell");
          } else {
            previewText = displayText;
          }
          if (cell.t === "n") {
            td.classList.add("num-cell");
          }
        }
        if (r === startRow) {
          td.classList.add("header-like");
        }
        td.textContent = displayText;
        td.dataset.addr = addr;
        td.dataset.preview = previewText;
        td.dataset.raw = displayText;

        td.addEventListener("click", () => {
          const prev = containerEl.querySelector("td.selected-cell");
          if (prev) prev.classList.remove("selected-cell");
          td.classList.add("selected-cell");
          cellRefBadge.textContent = addr;
          cellPreviewEl.textContent = previewText || "—";
          state.selectedCellText = displayText;
        });

        if (!firstCellSelected && displayText) {
          firstCellSelected = true;
          td.classList.add("selected-cell");
          cellRefBadge.textContent = addr;
          cellPreviewEl.textContent = previewText || "—";
          state.selectedCellText = displayText;
        }

        tr.appendChild(td);
      }
      tbody.appendChild(tr);
    }
    table.appendChild(tbody);
    containerEl.appendChild(table);

    if (!firstCellSelected) {
      cellRefBadge.textContent = "A1";
      cellPreviewEl.textContent = "—";
      state.selectedCellText = "";
    }

    if (renderRows < range.e.r) {
      moreBarEl.classList.remove("hidden");
      const remaining = range.e.r - renderRows;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "load-more-btn";
      btn.textContent = `+ Load ${Math.min(PAGE_ROW_CHUNK, remaining)} more rows (${remaining} remaining of ${totalRows})`;
      btn.addEventListener("click", () => {
        state.visibleRowLimit += PAGE_ROW_CHUNK;
        renderActiveSheet();
      });
      moreBarEl.appendChild(btn);
    }

    if (state.dirMode === "auto") {
      setDirectionMode("auto");
    }
    applyScale();
  }

  async function loadSpreadsheet(docUrl, ext, nightMode, dirMode) {
    try {
      setNightMode(!!nightMode);
      const res = await fetch(docUrl);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const buf = await res.arrayBuffer();
      const lowerExt = (ext || "").toLowerCase();

      let wb;
      if (lowerExt === "csv" || lowerExt === "tsv") {
        const text = new TextDecoder("utf-8").decode(buf);
        wb = XLSX.read(text, {
          type: "string",
          FS: lowerExt === "tsv" ? "\t" : undefined,
          raw: false
        });
      } else {
        wb = XLSX.read(new Uint8Array(buf), {
          type: "array",
          cellDates: true,
          cellFormula: true,
          cellNF: true
        });
      }

      if (!wb || !Array.isArray(wb.SheetNames) || wb.SheetNames.length === 0) {
        throw new Error("No worksheets found in file");
      }

      state.workbook = wb;
      state.sheetNames = wb.SheetNames;
      state.activeSheetIdx = 0;
      state.visibleRowLimit = PAGE_ROW_CHUNK;
      setDirectionMode(dirMode || "auto");

      renderTabs();
      renderActiveSheet();

      notify("onDocumentLoaded", state.sheetNames.length);
      notify("onPageChanged", 1, state.sheetNames.length);
    } catch (err) {
      notify("onDocumentError", err && err.message ? err.message : String(err));
    }
  }

  function goToPage(sheetOneBased) {
    if (state.sheetNames.length === 0) return;
    const idx = Math.max(0, Math.min(state.sheetNames.length - 1, (parseInt(sheetOneBased, 10) || 1) - 1));
    state.activeSheetIdx = idx;
    state.visibleRowLimit = PAGE_ROW_CHUNK;
    renderTabs();
    renderActiveSheet();
    notify("onPageChanged", idx + 1, state.sheetNames.length);
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
      notify("onSearchResult", 0, 0, state.activeSheetIdx + 1);
      return;
    }
    const needle = q.toLowerCase();
    const cells = containerEl.querySelectorAll("td");
    cells.forEach((td) => {
      const text = td.textContent || "";
      const lower = text.toLowerCase();
      if (!lower.includes(needle)) return;

      const frag = document.createDocumentFragment();
      let lastIdx = 0;
      let idx = lower.indexOf(needle);
      while (idx !== -1) {
        if (idx > lastIdx) frag.appendChild(document.createTextNode(text.slice(lastIdx, idx)));
        const mark = document.createElement("mark");
        mark.className = "docu-match";
        mark.textContent = text.slice(idx, idx + q.length);
        frag.appendChild(mark);
        state.matches.push(mark);
        lastIdx = idx + q.length;
        idx = lower.indexOf(needle, lastIdx);
      }
      if (lastIdx < text.length) frag.appendChild(document.createTextNode(text.slice(lastIdx)));
      td.textContent = "";
      td.appendChild(frag);
    });

    if (state.matches.length > 0) {
      state.activeMatchIdx = 0;
      activateMatch(0);
    } else {
      notify("onSearchResult", 0, 0, state.activeSheetIdx + 1);
    }
  }

  function activateMatch(index) {
    state.matches.forEach((m, i) => m.classList.toggle("active", i === index));
    const target = state.matches[index];
    if (target) target.scrollIntoView({ behavior: "smooth", block: "center", inline: "center" });
    notify("onSearchResult", index + 1, state.matches.length, state.activeSheetIdx + 1);
  }

  function stepSearch(delta) {
    if (state.matches.length === 0) return;
    state.activeMatchIdx = (state.activeMatchIdx + delta + state.matches.length) % state.matches.length;
    activateMatch(state.activeMatchIdx);
  }

  copyCellBtn.addEventListener("click", () => {
    if (state.selectedCellText) {
      notify("onTextCopied", state.selectedCellText);
    }
  });

  let lastSelectedText = "";
  const copyPill = document.createElement("div");
  copyPill.id = "docu-copy-pill";
  copyPill.textContent = /^ar/i.test(navigator.language || "") ? "📋 نسخ" : "📋 Copy";
  function handleCopyPillTap(e) {
    e.preventDefault();
    e.stopPropagation();
    const cur = window.getSelection ? window.getSelection().toString().trim() : "";
    const txt = cur || lastSelectedText;
    if (txt) {
      notify("onTextCopied", txt);
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

  function extractPageText(explicitSheet) {
    const req = parseInt(explicitSheet, 10);
    const idx = req > 0
      ? Math.max(0, Math.min(state.sheetNames.length - 1, req - 1))
      : state.activeSheetIdx;
    const sheetName = state.sheetNames[idx];
    const sheet = state.workbook && state.workbook.Sheets[sheetName];
    let text = "";
    if (sheet) {
      text = XLSX.utils.sheet_to_csv(sheet, { FS: "  |  " }).trim();
    }
    if (!text) {
      text = (containerEl.innerText || containerEl.textContent || "").trim();
    }
    notify("onPageTextExtracted", text ? text : "__DOCUVIEW_NO_TEXT_LAYER__");
    return text;
  }

  function extractAllPagesText() {
    const parts = [];
    if (state.workbook && Array.isArray(state.sheetNames)) {
      state.sheetNames.forEach((name, idx) => {
        const sheet = state.workbook.Sheets[name];
        if (sheet) {
          const csv = XLSX.utils.sheet_to_csv(sheet, { FS: "  |  " }).trim();
          if (csv) parts.push(`--- #${idx + 1} (${name}) ---\n${csv}`);
        }
      });
    }
    const combined = parts.join("\n\n").trim();
    notify("onPageTextExtracted", combined ? combined : "__DOCUVIEW_NO_TEXT_LAYER__");
    return combined;
  }

  function getSelectedText() {
    const liveSel = window.getSelection ? window.getSelection().toString().trim() : "";
    const chosen = liveSel || lastSelectedText || state.selectedCellText || "";
    if (chosen) {
      notify("onTextCopied", chosen);
      lastSelectedText = "";
      copyPill.classList.remove("visible");
      return chosen;
    }
    extractPageText(state.activeSheetIdx + 1);
    return "";
  }

  window.DocuExcel = {
    loadSpreadsheet,
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
  if (docParam) {
    loadSpreadsheet(
      docParam,
      params.get("ext") || "",
      params.get("night") === "1",
      params.get("dir") || "auto"
    );
  }
})();
