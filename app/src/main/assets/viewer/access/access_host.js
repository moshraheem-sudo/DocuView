(function () {
  "use strict";

  const state = {
    db: null,
    activeIndex: 0, // 0..tables.length-1 = tables; tables.length = SQL Queries tab (if any)
    showSchema: false,
    sortCol: -1,
    sortAsc: true,
    zoom: 1.0,
    nightMode: false,
    dirMode: "auto",
    selectedCellText: "",
    matches: [],
    activeMatchIdx: -1
  };

  const tabsEl = document.getElementById("db-object-tabs");
  const formatBadge = document.getElementById("db-format-badge");
  const summaryEl = document.getElementById("db-summary-text");
  const toggleSchemaBtn = document.getElementById("toggle-schema-btn");
  const schemaDrawer = document.getElementById("schema-drawer");
  const inspectorColEl = document.getElementById("inspector-col-name");
  const inspectorValEl = document.getElementById("inspector-cell-val");
  const copyCellBtn = document.getElementById("copy-cell-btn");
  const scaleHostEl = document.getElementById("db-scale-host");
  const contentEl = document.getElementById("db-content-container");

  function notify(fn, ...args) {
    if (window.AndroidBridge && typeof window.AndroidBridge[fn] === "function") {
      try {
        window.AndroidBridge[fn](...args);
      } catch (e) {
        console.error("Bridge error:", fn, e);
      }
    }
  }

  function totalObjectTabs() {
    if (!state.db) return 1;
    const tCount = state.db.tables.length;
    const hasQueries = state.db.queries.length > 0 ? 1 : 0;
    return Math.max(1, tCount + hasQueries);
  }

  function applyScale() {
    scaleHostEl.style.transform = `scale(${state.zoom})`;
    scaleHostEl.style.width = `${Math.round(100 / state.zoom)}%`;
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
      const hasArabic = /[\u0600-\u06FF]/.test(contentEl.textContent || "");
      document.body.classList.add(hasArabic ? "dir-rtl" : "dir-ltr");
    } else {
      document.body.classList.add("dir-" + state.dirMode);
    }
  }

  function renderTabs() {
    tabsEl.innerHTML = "";
    const tables = state.db.tables;
    tables.forEach((tbl, idx) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "db-tab-btn" + (idx === state.activeIndex ? " active" : "");
      const nameSpan = document.createElement("span");
      nameSpan.textContent = tbl.name;
      btn.appendChild(nameSpan);

      const countSpan = document.createElement("span");
      countSpan.className = "db-tab-count";
      countSpan.textContent = `(${tbl.rowCount})`;
      btn.appendChild(countSpan);

      btn.addEventListener("click", () => goToPage(idx + 1));
      tabsEl.appendChild(btn);
    });

    if (state.db.queries.length > 0) {
      const qIdx = tables.length;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "db-tab-btn" + (qIdx === state.activeIndex ? " active" : "");
      btn.textContent = `SQL Queries (${state.db.queries.length})`;
      btn.addEventListener("click", () => goToPage(qIdx + 1));
      tabsEl.appendChild(btn);
    }
  }

  function renderSchemaDrawer(table) {
    schemaDrawer.innerHTML = "";
    if (!table || !state.showSchema) {
      schemaDrawer.classList.add("hidden");
      toggleSchemaBtn.classList.remove("active");
      return;
    }
    schemaDrawer.classList.remove("hidden");
    toggleSchemaBtn.classList.add("active");
    table.columns.forEach((col) => {
      const pill = document.createElement("div");
      pill.className = "schema-pill";
      const n = document.createElement("span");
      n.textContent = col.name + (col.autoNumber ? " 🔑" : "");
      const t = document.createElement("span");
      t.className = "schema-type";
      t.textContent = col.type;
      pill.appendChild(n);
      pill.appendChild(t);
      schemaDrawer.appendChild(pill);
    });
  }

  function renderCurrentObject() {
    clearHighlights();
    contentEl.innerHTML = "";
    const tables = state.db.tables;
    const queries = state.db.queries;

    if (tables.length === 0 && queries.length === 0) {
      summaryEl.textContent = "0 tables";
      renderSchemaDrawer(null);
      const empty = document.createElement("div");
      empty.className = "empty-db-card";
      empty.textContent = "This Access database contains no user tables or queries.";
      contentEl.appendChild(empty);
      return;
    }

    if (state.activeIndex >= tables.length && queries.length > 0) {
      summaryEl.textContent = `${queries.length} saved SQL queries`;
      toggleSchemaBtn.style.display = "none";
      renderSchemaDrawer(null);
      inspectorColEl.textContent = "Query";
      inspectorValEl.textContent = queries[0].name;
      state.selectedCellText = queries[0].sql || queries[0].name;

      queries.forEach((q) => {
        const card = document.createElement("div");
        card.className = "query-card";
        const hdr = document.createElement("div");
        hdr.className = "query-header";
        const title = document.createElement("span");
        title.textContent = q.name;
        const badge = document.createElement("span");
        badge.className = "query-type";
        badge.textContent = q.type || "SELECT";
        hdr.appendChild(title);
        hdr.appendChild(badge);
        card.appendChild(hdr);

        const pre = document.createElement("pre");
        pre.className = "query-sql";
        pre.textContent = q.sql || "(No SQL text available)";
        card.appendChild(pre);
        contentEl.appendChild(card);
      });
      if (state.dirMode === "auto") setDirectionMode("auto");
      applyScale();
      return;
    }

    const table = tables[Math.min(state.activeIndex, tables.length - 1)];
    toggleSchemaBtn.style.display = "";
    summaryEl.textContent = `${table.name} • ${table.columns.length} cols • ${table.rowCount} rows`;
    renderSchemaDrawer(table);

    if (table.rows.length === 0) {
      const empty = document.createElement("div");
      empty.className = "empty-db-card";
      empty.textContent = `Table "${table.name}" has ${table.columns.length} columns and 0 rows.`;
      contentEl.appendChild(empty);
      inspectorColEl.textContent = "Table";
      inspectorValEl.textContent = table.name;
      state.selectedCellText = table.name;
      return;
    }

    const rows = table.rows.slice();
    if (state.sortCol >= 0 && state.sortCol < table.columns.length) {
      const colIdx = state.sortCol;
      const asc = state.sortAsc ? 1 : -1;
      rows.sort((a, b) => {
        const va = a[colIdx] || "";
        const vb = b[colIdx] || "";
        const na = Number(va);
        const nb = Number(vb);
        if (va !== "" && vb !== "" && !Number.isNaN(na) && !Number.isNaN(nb)) {
          return (na - nb) * asc;
        }
        return va.localeCompare(vb, undefined, { numeric: true, sensitivity: "base" }) * asc;
      });
    }

    const grid = document.createElement("table");
    grid.className = "db-grid";

    const thead = document.createElement("thead");
    const headTr = document.createElement("tr");
    const corner = document.createElement("th");
    corner.className = "row-num";
    corner.textContent = "#";
    headTr.appendChild(corner);

    table.columns.forEach((col, cIdx) => {
      const th = document.createElement("th");
      th.textContent = col.name;
      if (state.sortCol === cIdx) {
        const ind = document.createElement("span");
        ind.className = "sort-indicator";
        ind.textContent = state.sortAsc ? "▲" : "▼";
        th.appendChild(ind);
      }
      th.addEventListener("click", () => {
        if (state.sortCol === cIdx) {
          state.sortAsc = !state.sortAsc;
        } else {
          state.sortCol = cIdx;
          state.sortAsc = true;
        }
        renderCurrentObject();
      });
      headTr.appendChild(th);
    });
    thead.appendChild(headTr);
    grid.appendChild(thead);

    const tbody = document.createElement("tbody");
    let firstSelected = false;
    rows.forEach((rowArr, rIdx) => {
      const tr = document.createElement("tr");
      const rTh = document.createElement("th");
      rTh.className = "row-num";
      rTh.textContent = String(rIdx + 1);
      tr.appendChild(rTh);

      table.columns.forEach((col, cIdx) => {
        const td = document.createElement("td");
        const val = rowArr[cIdx] != null ? String(rowArr[cIdx]) : "";
        td.textContent = val;
        td.addEventListener("click", () => {
          const prev = contentEl.querySelector("td.selected-cell");
          if (prev) prev.classList.remove("selected-cell");
          td.classList.add("selected-cell");
          inspectorColEl.textContent = col.name;
          inspectorValEl.textContent = val || "—";
          state.selectedCellText = val;
        });
        if (!firstSelected && val) {
          firstSelected = true;
          td.classList.add("selected-cell");
          inspectorColEl.textContent = col.name;
          inspectorValEl.textContent = val;
          state.selectedCellText = val;
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    grid.appendChild(tbody);
    contentEl.appendChild(grid);

    if (table.truncated) {
      const note = document.createElement("div");
      note.className = "truncation-banner";
      note.textContent = `Showing first ${rows.length} of ${table.rowCount} records in "${table.name}".`;
      contentEl.appendChild(note);
    }

    if (state.dirMode === "auto") setDirectionMode("auto");
    applyScale();
  }

  async function loadDatabase(docUrl, nightMode, dirMode) {
    try {
      setNightMode(!!nightMode);
      const res = await fetch(docUrl);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();

      state.db = {
        fileName: data.fileName || "Database",
        fileFormat: data.fileFormat || "ACCESS",
        tables: Array.isArray(data.tables) ? data.tables : [],
        queries: Array.isArray(data.queries) ? data.queries : []
      };
      state.activeIndex = 0;
      state.sortCol = -1;
      state.sortAsc = true;
      state.dirMode = dirMode || "auto";

      formatBadge.textContent = state.db.fileFormat;
      renderTabs();
      renderCurrentObject();

      const total = totalObjectTabs();
      notify("onDocumentLoaded", total);
      notify("onPageChanged", 1, total);
    } catch (err) {
      notify("onDocumentError", err && err.message ? err.message : String(err));
    }
  }

  function goToPage(oneBased) {
    const total = totalObjectTabs();
    const idx = Math.max(0, Math.min(total - 1, (parseInt(oneBased, 10) || 1) - 1));
    state.activeIndex = idx;
    state.sortCol = -1;
    state.sortAsc = true;
    renderTabs();
    renderCurrentObject();
    notify("onPageChanged", idx + 1, total);
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
      notify("onSearchResult", 0, 0, state.activeIndex + 1);
      return;
    }
    const needle = q.toLowerCase();
    const targets = contentEl.querySelectorAll("td, pre.query-sql, .query-header span:first-child");
    targets.forEach((el) => {
      const text = el.textContent || "";
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
      el.textContent = "";
      el.appendChild(frag);
    });

    if (state.matches.length > 0) {
      state.activeMatchIdx = 0;
      activateMatch(0);
    } else {
      notify("onSearchResult", 0, 0, state.activeIndex + 1);
    }
  }

  function activateMatch(index) {
    state.matches.forEach((m, i) => m.classList.toggle("active", i === index));
    const target = state.matches[index];
    if (target) target.scrollIntoView({ behavior: "smooth", block: "center", inline: "center" });
    notify("onSearchResult", index + 1, state.matches.length, state.activeIndex + 1);
  }

  function stepSearch(delta) {
    if (state.matches.length === 0) return;
    state.activeMatchIdx = (state.activeMatchIdx + delta + state.matches.length) % state.matches.length;
    activateMatch(state.activeMatchIdx);
  }

  toggleSchemaBtn.addEventListener("click", () => {
    state.showSchema = !state.showSchema;
    const tbl = state.db && state.db.tables[state.activeIndex];
    renderSchemaDrawer(tbl || null);
  });

  copyCellBtn.addEventListener("click", () => {
    if (state.selectedCellText) {
      notify("onTextCopied", state.selectedCellText);
    }
  });

  function extractPageText() {
    const text = (contentEl.innerText || contentEl.textContent || "").trim();
    notify("onPageTextExtracted", text ? text : "__DOCUVIEW_NO_TEXT_LAYER__");
    return text;
  }

  function extractAllPagesText() {
    if (!state.db) {
      notify("onPageTextExtracted", "__DOCUVIEW_NO_TEXT_LAYER__");
      return "";
    }
    const parts = [];
    (state.db.tables || []).forEach((tbl, idx) => {
      const header = (tbl.columns || []).map((c) => c.name).join("  |  ");
      const rows = (tbl.rows || []).map((r) => r.map((v) => (v == null ? "" : String(v))).join("  |  "));
      const tableText = [header, ...rows].filter(Boolean).join("\n").trim();
      if (tableText) parts.push(`--- #${idx + 1} (${tbl.name}) ---\n${tableText}`);
    });
    (state.db.queries || []).forEach((q, idx) => {
      if (q.sql) parts.push(`--- Query #${idx + 1} (${q.name}) ---\n${q.sql}`);
    });
    const combined = parts.join("\n\n").trim();
    notify("onPageTextExtracted", combined ? combined : "__DOCUVIEW_NO_TEXT_LAYER__");
    return combined;
  }

  function getSelectedText() {
    const liveSel = window.getSelection ? window.getSelection().toString().trim() : "";
    const chosen = liveSel || state.selectedCellText || "";
    if (chosen) {
      notify("onTextCopied", chosen);
      return chosen;
    }
    extractPageText();
    return "";
  }

  window.DocuAccess = {
    loadDatabase,
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
    loadDatabase(docParam, params.get("night") === "1", params.get("dir") || "auto");
  }
})();
