(function () {
  "use strict";

  const state = {
    totalSlides: 0,
    currentSlide: 1,
    zoom: 1.0,
    presentationMode: false,
    singleSlideMode: true,
    nightMode: false,
    matches: [],
    activeMatchIdx: -1,
    slideFrames: [],
    touchStartX: 0,
    touchStartY: 0,
    wasMultiTouch: false,
    zip: null,
    emuW: 9144000,
    emuH: 5143500
  };

  const stage = document.getElementById("slides-stage");
  let lastSelectedText = "";

  // Provide JSZipUtils.getBinaryContent required by PPTXjs when loading via pptxFileUrl,
  // and polyfill any optional OpenXML entries so PPTXjs never fails or gets stuck on Loading... (1%).
  window.JSZipUtils = window.JSZipUtils || {
    getBinaryContent: function (url, callback) {
      fetch(url)
        .then((res) => {
          if (!res.ok) throw new Error("HTTP " + res.status);
          return res.arrayBuffer();
        })
        .then((buf) => {
          try {
            if (typeof JSZip !== "undefined") {
              const zip = new JSZip(buf);
              state.zip = zip;
              let modified = false;

              const appFile = zip.file("docProps/app.xml");
              if (!appFile) {
                zip.file(
                  "docProps/app.xml",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><TotalTime>1</TotalTime><AppVersion>16.0000</AppVersion></Properties>'
                );
                modified = true;
              } else {
                const appXml = appFile.asText();
                if (appXml.indexOf("AppVersion") === -1) {
                  zip.file(
                    "docProps/app.xml",
                    appXml.replace(
                      /<\/Properties>/i,
                      "<AppVersion>16.0000</AppVersion></Properties>"
                    )
                  );
                  modified = true;
                }
              }

              if (!zip.file("ppt/tableStyles.xml")) {
                zip.file(
                  "ppt/tableStyles.xml",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><a:tblStyleLst xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" def="{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}"/>'
                );
                modified = true;
              }
              if (!zip.file("ppt/theme/theme1.xml")) {
                zip.file(
                  "ppt/theme/theme1.xml",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Office Theme"><a:themeElements><a:clrScheme name="Office"><a:dk1><a:srgbClr val="0F172A"/></a:dk1><a:lt1><a:srgbClr val="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="1E293B"/></a:dk2><a:lt2><a:srgbClr val="F8FAFC"/></a:lt2><a:accent1><a:srgbClr val="2563EB"/></a:accent1><a:accent2><a:srgbClr val="EA580C"/></a:accent2><a:accent3><a:srgbClr val="16A34A"/></a:accent3><a:accent4><a:srgbClr val="7C3AED"/></a:accent4><a:accent5><a:srgbClr val="0891B2"/></a:accent5><a:accent6><a:srgbClr val="DB2777"/></a:accent6><a:hlink><a:srgbClr val="2563EB"/></a:hlink><a:folHlink><a:srgbClr val="7C3AED"/></a:folHlink></a:clrScheme><a:fontScheme name="Office"><a:majorFont><a:latin typeface="Calibri"/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/></a:minorFont></a:fontScheme><a:fmtScheme name="Office"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>'
                );
                modified = true;
              }
              if (!zip.file("ppt/slideMasters/slideMaster1.xml")) {
                zip.file(
                  "ppt/slideMasters/slideMaster1.xml",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/></p:sldMaster>'
                );
                modified = true;
              }
              if (!zip.file("ppt/slideMasters/_rels/slideMaster1.xml.rels")) {
                zip.file(
                  "ppt/slideMasters/_rels/slideMaster1.xml.rels",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>'
                );
                modified = true;
              }
              if (!zip.file("ppt/slideLayouts/slideLayout1.xml")) {
                zip.file(
                  "ppt/slideLayouts/slideLayout1.xml",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld></p:sldLayout>'
                );
                modified = true;
              }
              if (!zip.file("ppt/slideLayouts/_rels/slideLayout1.xml.rels")) {
                zip.file(
                  "ppt/slideLayouts/_rels/slideLayout1.xml.rels",
                  '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>'
                );
                modified = true;
              }

              // Read slide size in EMUs from ppt/presentation.xml if present
              const presFile = zip.file("ppt/presentation.xml");
              if (presFile) {
                try {
                  const parser = new DOMParser();
                  const presDoc = parser.parseFromString(presFile.asText(), "application/xml");
                  const sldSz = presDoc.getElementsByTagNameNS("*", "sldSz")[0];
                  if (sldSz) {
                    const cx = parseInt(sldSz.getAttribute("cx") || "9144000", 10);
                    const cy = parseInt(sldSz.getAttribute("cy") || "5143500", 10);
                    if (cx > 0 && cy > 0) {
                      state.emuW = cx;
                      state.emuH = cy;
                    }
                  }
                } catch (_) {}
              }

              Object.keys(zip.files).forEach((k) => {
                const m = k.match(/^ppt\/slides\/slide(\d+)\.xml$/i);
                if (m) {
                  const relPath = `ppt/slides/_rels/slide${m[1]}.xml.rels`;
                  if (!zip.file(relPath)) {
                    zip.file(
                      relPath,
                      '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>'
                    );
                    modified = true;
                  }
                }
              });

              if (modified) {
                const out = zip.generate({ type: "arraybuffer" });
                callback(null, out);
                return;
              }
            }
          } catch (_) {}
          callback(null, buf);
        })
        .catch((err) => callback(err, null));
    }
  };

  function notify(fn, ...args) {
    if (window.AndroidBridge && typeof window.AndroidBridge[fn] === "function") {
      try {
        window.AndroidBridge[fn](...args);
      } catch (e) {
        console.error("Bridge error:", fn, e);
      }
    }
  }

  function layoutSlides() {
    const availW = Math.max(260, window.innerWidth - (state.presentationMode ? 12 : 16));
    const availH = Math.max(200, window.innerHeight - (state.presentationMode ? 24 : 120));

    state.slideFrames.forEach((frame, idx) => {
      const slideEl = frame.querySelector(".slide");
      if (!slideEl) return;

      const rawW = parseFloat(slideEl.style.width) || slideEl.offsetWidth || 960;
      const rawH = parseFloat(slideEl.style.height) || slideEl.offsetHeight || 540;

      let fitScale = availW / rawW;
      if (state.presentationMode || state.singleSlideMode) {
        fitScale = Math.min(availW / rawW, availH / rawH);
      }
      const scale = fitScale * state.zoom;

      slideEl.style.transform = `scale(${scale})`;
      frame.style.width = Math.round(rawW * scale) + "px";
      frame.style.height = Math.round(rawH * scale) + "px";

      const isVisible = !state.singleSlideMode && !state.presentationMode
        ? true
        : (idx + 1 === state.currentSlide);
      frame.classList.toggle("hidden-slide", !isVisible);
    });
  }

  function fixDiagramAndBackgroundLayering(slideEl, slideW, slideH) {
    // 1. Fix SmartArt .diagram-content double coordinate offset:
    // In PowerPoint OpenXML, ppt/diagrams/drawing*.xml shapes often already use slide-level coordinates,
    // while PPTXjs also offsets the parent .diagram-content div by <p:graphicFrame><p:xfrm><a:off>.
    const diagrams = slideEl.querySelectorAll(".diagram-content");
    diagrams.forEach((dgm) => {
      const dLeft = parseFloat(dgm.style.left) || 0;
      const dTop = parseFloat(dgm.style.top) || 0;
      if (dLeft > 2 || dTop > 2) {
        let minX = Infinity;
        let maxRight = 0;
        let maxBottom = 0;
        const children = dgm.querySelectorAll("div.block, svg.drawing");
        children.forEach((ch) => {
          const l = parseFloat(ch.style.left);
          const t = parseFloat(ch.style.top);
          const w = parseFloat(ch.style.width) || 0;
          const h = parseFloat(ch.style.height) || 0;
          if (!isNaN(l) && w > 0) {
            if (l < minX) minX = l;
            if (l + w > maxRight) maxRight = l + w;
          }
          if (!isNaN(t) && h > 0) {
            if (t + h > maxBottom) maxBottom = t + h;
          }
        });
        if (
          dLeft + maxRight > slideW + 8 ||
          dTop + maxBottom > slideH + 8 ||
          (minX < Infinity && minX >= dLeft * 0.65)
        ) {
          dgm.style.left = "0px";
          dgm.style.top = "0px";
          dgm.style.width = slideW + "px";
          dgm.style.height = slideH + "px";
        }
      }
    });

    // 2. Ensure large background shapes never cover text blocks
    const fg = slideEl.querySelector(".slide-foreground") || slideEl;
    const allEls = fg.querySelectorAll("svg.drawing, div.block");
    allEls.forEach((el) => {
      if (el.classList.contains("group") || el.classList.contains("diagram-content")) return;
      const w = parseFloat(el.style.width) || 0;
      const h = parseFloat(el.style.height) || 0;
      const hasText = (el.textContent || "").replace(/\s+/g, "").length > 0;
      const isLargeBlankShape = !hasText && w >= slideW * 0.65 && h >= slideH * 0.65;
      if (isLargeBlankShape) {
        el.style.zIndex = "1";
        el.style.pointerEvents = "none";
      } else if (hasText && el.tagName === "DIV") {
        const curZ = parseInt(el.style.zIndex || "10", 10) || 10;
        el.style.zIndex = String(Math.max(25, curZ));
      }
    });
  }

  function recoverMissingSlideTextFromXml(slideEl, slideNum, slideW, slideH) {
    if (!state.zip) return;
    const slideFile = state.zip.file(`ppt/slides/slide${slideNum}.xml`);
    if (!slideFile) return;

    try {
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(slideFile.asText(), "application/xml");
      const fg = slideEl.querySelector(".slide-foreground") || slideEl;
      const renderedText = (slideEl.innerText || slideEl.textContent || "").replace(/\s+/g, "");

      const toPxX = (emu) => Math.round((parseFloat(emu || 0) / (state.emuW || 9144000)) * slideW);
      const toPxY = (emu) => Math.round((parseFloat(emu || 0) / (state.emuH || 5143500)) * slideH);

      // Inspect every shape <p:sp> in slideN.xml
      const spNodes = xmlDoc.getElementsByTagNameNS("*", "sp");
      let fallbackTop = 48;

      for (let i = 0; i < spNodes.length; i++) {
        const sp = spNodes[i];
        const txBody = sp.getElementsByTagNameNS("*", "txBody")[0];
        if (!txBody) continue;

        const tNodes = txBody.getElementsByTagNameNS("*", "t");
        let shapeRawText = "";
        for (let t = 0; t < tNodes.length; t++) {
          shapeRawText += tNodes[t].textContent || "";
        }
        const normalizedShapeText = shapeRawText.replace(/\s+/g, "");
        if (!normalizedShapeText) continue;

        // Check if this shape's text is already visibly rendered in slideEl
        const sampleProbe = normalizedShapeText.slice(0, Math.min(10, normalizedShapeText.length));
        if (renderedText.indexOf(sampleProbe) !== -1) {
          // Even if present in DOM, verify its block isn't 0x0 or off-screen or font-size 0
          let foundVisibleBlock = false;
          const blocks = fg.querySelectorAll("div.block");
          blocks.forEach((b) => {
            const bTxt = (b.textContent || "").replace(/\s+/g, "");
            if (bTxt.indexOf(sampleProbe) !== -1) {
              const bw = parseFloat(b.style.width) || b.offsetWidth || 0;
              const bh = parseFloat(b.style.height) || b.offsetHeight || 0;
              const bl = parseFloat(b.style.left);
              const bt = parseFloat(b.style.top);
              if (bw >= 20 && bh >= 14 && (isNaN(bl) || (bl > -slideW * 0.5 && bl < slideW * 0.95)) && (isNaN(bt) || (bt > -slideH * 0.5 && bt < slideH * 0.95))) {
                foundVisibleBlock = true;
              } else {
                // Fix broken placeholder coordinates directly
                b.style.left = Math.round(slideW * 0.08) + "px";
                b.style.top = fallbackTop + "px";
                b.style.width = Math.round(slideW * 0.84) + "px";
                b.style.minHeight = "64px";
                b.style.zIndex = "45";
                fallbackTop += 84;
                foundVisibleBlock = true;
              }
            }
          });
          if (foundVisibleBlock) continue;
        }

        // Render missing shape text onto the slide
        const spPr = sp.getElementsByTagNameNS("*", "spPr")[0];
        const xfrm = spPr ? spPr.getElementsByTagNameNS("*", "xfrm")[0] : null;
        const off = xfrm ? xfrm.getElementsByTagNameNS("*", "off")[0] : null;
        const ext = xfrm ? xfrm.getElementsByTagNameNS("*", "ext")[0] : null;

        const phNode = sp.getElementsByTagNameNS("*", "ph")[0];
        const phType = phNode ? (phNode.getAttribute("type") || "") : "";
        const isTitle = phType === "ctrTitle" || phType === "title" || i === 0;

        let x = off && off.getAttribute("x") !== null ? toPxX(off.getAttribute("x")) : Math.round(slideW * 0.08);
        let y = off && off.getAttribute("y") !== null ? toPxY(off.getAttribute("y")) : (isTitle ? Math.round(slideH * 0.22) : fallbackTop);
        let w = ext && ext.getAttribute("cx") !== null ? Math.max(80, toPxX(ext.getAttribute("cx"))) : Math.round(slideW * 0.84);
        let h = ext && ext.getAttribute("cy") !== null ? Math.max(36, toPxY(ext.getAttribute("cy"))) : (isTitle ? 110 : 72);

        if (x < 0 || x >= slideW - 40) x = Math.round(slideW * 0.08);
        if (y < 0 || y >= slideH - 30) y = fallbackTop;
        if (x + w > slideW) w = Math.max(120, slideW - x - 16);
        fallbackTop = Math.min(slideH - 60, y + h + 16);

        const box = document.createElement("div");
        box.className = "block content v-mid";
        box.style.position = "absolute";
        box.style.left = x + "px";
        box.style.top = y + "px";
        box.style.width = w + "px";
        box.style.minHeight = h + "px";
        box.style.zIndex = "50";
        box.style.padding = "8px 14px";
        box.style.boxSizing = "border-box";

        const pNodes = txBody.getElementsByTagNameNS("*", "p");
        for (let pIdx = 0; pIdx < pNodes.length; pIdx++) {
          const pNode = pNodes[pIdx];
          const pTxt = (pNode.textContent || "").trim();
          if (!pTxt) continue;

          const pDiv = document.createElement("div");
          pDiv.className = "slide-prgrph";
          pDiv.style.unicodeBidi = "plaintext";
          const isArabic = /[\u0600-\u06FF]/.test(pTxt);
          pDiv.style.direction = isArabic ? "rtl" : "ltr";
          pDiv.style.textAlign = isTitle ? "center" : (isArabic ? "right" : "left");
          pDiv.style.fontSize = isTitle ? "26px" : "18px";
          pDiv.style.fontWeight = isTitle ? "700" : "500";
          pDiv.style.color = "#1e293b";
          pDiv.style.marginBottom = "6px";
          pDiv.textContent = pTxt;
          box.appendChild(pDiv);
        }

        if (box.childNodes.length > 0) {
          fg.appendChild(box);
        }
      }
    } catch (e) {
      console.warn("Slide XML text recovery check error:", e);
    }
  }

  function autoFitSlideTextBlocks(slideEl, slideW, slideH) {
    const blocks = slideEl.querySelectorAll("div.block");
    blocks.forEach((b) => {
      if (b.classList.contains("group") || b.classList.contains("diagram-content")) return;

      const text = (b.textContent || "").trim();
      if (!text) return;

      // Ensure RTL paragraphs have clean direction & alignment
      if (/[\u0600-\u06FF]/.test(text)) {
        const prgrphs = b.querySelectorAll(".slide-prgrph");
        prgrphs.forEach((p) => {
          p.style.direction = "rtl";
          if (!p.classList.contains("h-mid") && !p.classList.contains("h-left")) {
            p.style.textAlign = "right";
          }
        });
      }

      // Ensure spans with inherited/0px font-size have a readable size
      const spans = b.querySelectorAll(".text-block, span");
      spans.forEach((sp) => {
        const computedSz = parseFloat(window.getComputedStyle(sp).fontSize);
        if (isNaN(computedSz) || computedSz < 7) {
          sp.style.fontSize = "14px";
        }
      });

      const boxW = parseFloat(b.style.width) || b.clientWidth || 0;
      const boxH = parseFloat(b.style.height) || b.clientHeight || 0;
      if (boxW <= 12 || boxH <= 12) return;

      // Check if rotated vertical label (e.g. -90deg or 270deg "القيمة" badge)
      const transformStr = b.style.transform || "";
      const rotMatch = transformStr.match(/rotate\(([-\d.]+)deg\)/i);
      const rotDeg = rotMatch ? parseFloat(rotMatch[1]) : 0;
      const isRotated90 = Math.abs(Math.abs(rotDeg) % 180 - 90) < 15;

      if (isRotated90 && text.length <= 18) {
        b.style.whiteSpace = "nowrap";
        b.style.display = "flex";
        b.style.alignItems = "center";
        b.style.justifyContent = "center";
      }

      // Iteratively scale down font size inside block if content overflows box height or width
      let guard = 0;
      while (
        guard < 7 &&
        (b.scrollHeight > boxH + 4 || (!isRotated90 && b.scrollWidth > boxW + 8))
      ) {
        guard++;
        const ratioH = boxH / Math.max(1, b.scrollHeight);
        const ratioW = isRotated90 ? 1 : boxW / Math.max(1, b.scrollWidth);
        const factor = Math.max(0.76, Math.min(0.92, Math.min(ratioH, ratioW)));

        let changed = false;
        spans.forEach((sp) => {
          const cur = parseFloat(sp.style.fontSize) || parseFloat(window.getComputedStyle(sp).fontSize) || 14;
          if (cur > 8.5) {
            sp.style.fontSize = Math.max(8, Math.round(cur * factor * 10) / 10) + "px";
            changed = true;
          }
        });
        if (!changed) break;
      }
    });
  }

  function fitSlideContentBounds(slideEl, slideW, slideH) {
    const fg = slideEl.querySelector(".slide-foreground");
    if (!fg) return;

    const elements = Array.from(fg.querySelectorAll("svg.drawing, div.block")).filter((el) => {
      if (el.classList.contains("group") || el.classList.contains("diagram-content")) return false;
      const w = parseFloat(el.style.width) || 0;
      const h = parseFloat(el.style.height) || 0;
      // Ignore full-slide background rectangles when computing foreground content bounds
      if (w >= slideW * 0.92 && h >= slideH * 0.92) return false;
      return w > 2 && h > 2;
    });

    if (elements.length === 0) return;

    let minL = Infinity;
    let minT = Infinity;
    let maxR = -Infinity;
    let maxB = -Infinity;

    elements.forEach((el) => {
      let l = parseFloat(el.style.left);
      let t = parseFloat(el.style.top);
      const w = parseFloat(el.style.width) || 0;
      const h = parseFloat(el.style.height) || 0;
      if (isNaN(l) || isNaN(t)) return;

      // Account for parent group/diagram offset if any
      let parent = el.parentElement;
      while (parent && parent !== fg && parent !== slideEl) {
        l += parseFloat(parent.style.left) || 0;
        t += parseFloat(parent.style.top) || 0;
        parent = parent.parentElement;
      }

      if (l < minL) minL = l;
      if (t < minT) minT = t;
      if (l + w > maxR) maxR = l + w;
      if (t + h > maxB) maxB = t + h;
    });

    if (!isFinite(minL) || !isFinite(maxR) || maxR <= minL) return;

    const contentW = maxR - minL;
    const contentH = maxB - minT;

    // If content overflows slide right/bottom or is noticeably shifted off-center
    const overflowsRight = maxR > slideW + 6;
    const overflowsBottom = maxB > slideH + 6;
    const overflowsLeft = minL < -6;
    const overflowsTop = minT < -6;
    const asymmetricShift = minL > 42 && maxR > slideW - 8;

    if (overflowsRight || overflowsBottom || overflowsLeft || overflowsTop || asymmetricShift) {
      const targetPadX = 12;
      const targetPadY = 10;
      const availW = slideW - targetPadX * 2;
      const availH = slideH - targetPadY * 2;

      const scaleX = contentW > availW ? availW / contentW : 1;
      const scaleY = contentH > availH ? availH / contentH : 1;
      const fitScale = Math.min(1, scaleX, scaleY);

      const scaledW = contentW * fitScale;
      const scaledH = contentH * fitScale;

      const targetLeft = Math.round((slideW - scaledW) / 2 - minL * fitScale);
      const targetTop = Math.round((slideH - scaledH) / 2 - minT * fitScale);

      fg.style.transformOrigin = "0 0";
      fg.style.transform = `translate(${targetLeft}px, ${targetTop}px) scale(${fitScale})`;
    }
  }

  function repairAndNormalizeSlide(slideEl, slideNum) {
    const slideW = parseFloat(slideEl.style.width) || slideEl.offsetWidth || 960;
    const slideH = parseFloat(slideEl.style.height) || slideEl.offsetHeight || 540;

    fixDiagramAndBackgroundLayering(slideEl, slideW, slideH);
    recoverMissingSlideTextFromXml(slideEl, slideNum, slideW, slideH);
    autoFitSlideTextBlocks(slideEl, slideW, slideH);
    fitSlideContentBounds(slideEl, slideW, slideH);
  }

  function wrapRenderedSlides() {
    // Remove any leftover loading bar immediately
    document.querySelectorAll(".slides-loadnig-msg").forEach((el) => el.remove());

    const rawSlides = Array.from(stage.querySelectorAll(".slide"));
    state.slideFrames = [];
    const summaries = [];

    rawSlides.forEach((slideEl, idx) => {
      const slideNum = idx + 1;
      const frame = document.createElement("div");
      frame.className = "slide-frame";
      frame.dataset.slide = String(slideNum);

      const parent = slideEl.parentNode;
      parent.insertBefore(frame, slideEl);
      frame.appendChild(slideEl);

      repairAndNormalizeSlide(slideEl, slideNum);

      const badge = document.createElement("div");
      badge.className = "slide-badge";
      badge.textContent = String(slideNum);
      frame.appendChild(badge);

      state.slideFrames.push(frame);

      const rawText = (slideEl.innerText || slideEl.textContent || "").replace(/\s+/g, " ").trim();
      summaries.push({
        index: slideNum,
        snippet: rawText.slice(0, 90)
      });
    });

    state.totalSlides = Math.max(1, state.slideFrames.length);
    state.currentSlide = 1;
    layoutSlides();
    notify("onSlideSummaries", JSON.stringify(summaries));
    notify("onDocumentLoaded", state.totalSlides);
    notify("onPageChanged", 1, state.totalSlides);
  }

  // Rich Visual 16:9 OpenXML Slide Renderer fallback
  async function renderVisualOpenXmlSlides(docUrl) {
    try {
      if (typeof JSZip === "undefined") return false;
      const res = await fetch(docUrl);
      if (!res.ok) return false;
      const buf = await res.arrayBuffer();
      const zip = new JSZip(buf);
      state.zip = zip;
      const slideKeys = Object.keys(zip.files)
        .filter((k) => /^ppt\/slides\/slide\d+\.xml$/i.test(k.replace(/\\/g, "/")))
        .sort((a, b) => {
          const na = parseInt((a.match(/slide(\d+)\.xml/i) || [0, 0])[1], 10);
          const nb = parseInt((b.match(/slide(\d+)\.xml/i) || [0, 0])[1], 10);
          return na - nb;
        });

      if (slideKeys.length === 0) return false;

      const parser = new DOMParser();
      let slideW = 960;
      let slideH = 540;
      let emuW = 9144000;
      let emuH = 5143500;

      const presFile = zip.file("ppt/presentation.xml");
      if (presFile) {
        const presDoc = parser.parseFromString(presFile.asText(), "application/xml");
        const sldSz = presDoc.getElementsByTagNameNS("*", "sldSz")[0];
        if (sldSz) {
          const cx = parseInt(sldSz.getAttribute("cx") || "9144000", 10);
          const cy = parseInt(sldSz.getAttribute("cy") || "5143500", 10);
          if (cx > 0 && cy > 0) {
            emuW = cx;
            emuH = cy;
            state.emuW = cx;
            state.emuH = cy;
            slideW = 960;
            slideH = Math.round((960 * cy) / cx);
          }
        }
      }

      const toPxX = (emu) => Math.round((parseFloat(emu || 0) / emuW) * slideW);
      const toPxY = (emu) => Math.round((parseFloat(emu || 0) / emuH) * slideH);

      stage.innerHTML = "";
      const slidesContainer = document.createElement("div");
      slidesContainer.className = "slides";

      slideKeys.forEach((key, slideIdx) => {
        const slideNum = slideIdx + 1;
        const xmlStr = zip.file(key).asText();
        const xmlDoc = parser.parseFromString(xmlStr, "application/xml");

        const relMap = {};
        const relFile = zip.file(`ppt/slides/_rels/slide${slideNum}.xml.rels`);
        if (relFile) {
          const relDoc = parser.parseFromString(relFile.asText(), "application/xml");
          const relNodes = relDoc.getElementsByTagNameNS("*", "Relationship");
          for (let r = 0; r < relNodes.length; r++) {
            const id = relNodes[r].getAttribute("Id");
            const target = relNodes[r].getAttribute("Target") || "";
            if (id && target) {
              relMap[id] = target.replace(/^\.\.\//, "ppt/").replace(/^\//, "");
            }
          }
        }

        const slideDiv = document.createElement("div");
        slideDiv.className = "slide";
        slideDiv.style.width = slideW + "px";
        slideDiv.style.height = slideH + "px";
        slideDiv.style.position = "relative";
        slideDiv.style.overflow = "hidden";
        slideDiv.style.background = "#ffffff";
        slideDiv.style.boxSizing = "border-box";

        const fgDiv = document.createElement("div");
        fgDiv.className = "slide-foreground";
        fgDiv.style.position = "absolute";
        fgDiv.style.top = "0";
        fgDiv.style.left = "0";
        fgDiv.style.width = "100%";
        fgDiv.style.height = "100%";
        fgDiv.style.zIndex = "2";
        slideDiv.appendChild(fgDiv);

        const bgNode = xmlDoc.getElementsByTagNameNS("*", "bg")[0];
        if (bgNode) {
          const bgClr = bgNode.getElementsByTagNameNS("*", "srgbClr")[0];
          if (bgClr && bgClr.getAttribute("val")) {
            slideDiv.style.background = "#" + bgClr.getAttribute("val");
          }
        }

        let placedElements = 0;

        const spNodes = xmlDoc.getElementsByTagNameNS("*", "sp");
        for (let i = 0; i < spNodes.length; i++) {
          const sp = spNodes[i];
          const spPr = sp.getElementsByTagNameNS("*", "spPr")[0];
          const xfrm = spPr ? spPr.getElementsByTagNameNS("*", "xfrm")[0] : null;
          const off = xfrm ? xfrm.getElementsByTagNameNS("*", "off")[0] : null;
          const ext = xfrm ? xfrm.getElementsByTagNameNS("*", "ext")[0] : null;

          const x = off ? toPxX(off.getAttribute("x")) : 48;
          const y = off ? toPxY(off.getAttribute("y")) : 40 + i * 70;
          const w = ext ? Math.max(40, toPxX(ext.getAttribute("cx"))) : slideW - 96;
          const h = ext ? Math.max(24, toPxY(ext.getAttribute("cy"))) : 60;

          const box = document.createElement("div");
          box.className = "block content";
          box.style.position = "absolute";
          box.style.left = x + "px";
          box.style.top = y + "px";
          box.style.width = w + "px";
          box.style.minHeight = h + "px";
          box.style.boxSizing = "border-box";
          box.style.padding = "8px 12px";

          if (spPr) {
            const prstGeom = spPr.getElementsByTagNameNS("*", "prstGeom")[0];
            if (prstGeom && prstGeom.getAttribute("prst") === "roundRect") {
              box.style.borderRadius = "12px";
            }
            for (let c = 0; c < spPr.childNodes.length; c++) {
              const child = spPr.childNodes[c];
              if (child.localName === "solidFill") {
                const srgb = child.getElementsByTagNameNS("*", "srgbClr")[0];
                if (srgb && srgb.getAttribute("val")) {
                  box.style.backgroundColor = "#" + srgb.getAttribute("val");
                }
              }
            }
          }

          const txBody = sp.getElementsByTagNameNS("*", "txBody")[0];
          if (txBody) {
            const pNodes = txBody.getElementsByTagNameNS("*", "p");
            for (let pIdx = 0; pIdx < pNodes.length; pIdx++) {
              const pNode = pNodes[pIdx];
              const pEl = document.createElement("div");
              pEl.className = "slide-prgrph";
              pEl.style.marginBottom = "4px";
              pEl.style.unicodeBidi = "plaintext";

              const pPr = pNode.getElementsByTagNameNS("*", "pPr")[0];
              if (pPr) {
                const algn = pPr.getAttribute("algn");
                if (algn === "ctr") pEl.style.textAlign = "center";
                else if (algn === "r") pEl.style.textAlign = "right";
                if (pPr.getAttribute("rtl") === "1") {
                  pEl.style.direction = "rtl";
                  if (!algn) pEl.style.textAlign = "right";
                }
              }

              const rNodes = pNode.getElementsByTagNameNS("*", "r");
              if (rNodes.length > 0) {
                for (let rIdx = 0; rIdx < rNodes.length; rIdx++) {
                  const rNode = rNodes[rIdx];
                  const tNode = rNode.getElementsByTagNameNS("*", "t")[0];
                  if (!tNode || !tNode.textContent) continue;

                  const span = document.createElement("span");
                  span.className = "text-block";
                  span.textContent = tNode.textContent;

                  const rPr = rNode.getElementsByTagNameNS("*", "rPr")[0];
                  if (rPr) {
                    const sz = parseInt(rPr.getAttribute("sz") || "0", 10);
                    if (sz > 0) {
                      span.style.fontSize = Math.max(10, Math.round(sz / 100)) + "px";
                    } else {
                      span.style.fontSize = "16px";
                    }
                    if (rPr.getAttribute("b") === "1") span.style.fontWeight = "700";
                    if (rPr.getAttribute("i") === "1") span.style.fontStyle = "italic";
                    const clr = rPr.getElementsByTagNameNS("*", "srgbClr")[0];
                    if (clr && clr.getAttribute("val")) {
                      span.style.color = "#" + clr.getAttribute("val");
                    } else {
                      span.style.color = "#0f172a";
                    }
                  } else {
                    span.style.fontSize = "16px";
                    span.style.color = "#0f172a";
                  }
                  pEl.appendChild(span);
                }
              } else {
                const tNodes = pNode.getElementsByTagNameNS("*", "t");
                let txt = "";
                for (let tIdx = 0; tIdx < tNodes.length; tIdx++) {
                  txt += tNodes[tIdx].textContent || "";
                }
                if (txt.trim()) {
                  pEl.textContent = txt.trim();
                  pEl.style.fontSize = "16px";
                  pEl.style.color = "#0f172a";
                }
              }

              if (pEl.textContent && pEl.textContent.trim()) {
                box.appendChild(pEl);
              }
            }
          }

          if (box.style.backgroundColor || box.childNodes.length > 0) {
            fgDiv.appendChild(box);
            placedElements++;
          }
        }

        const tblNodes = xmlDoc.getElementsByTagNameNS("*", "tbl");
        for (let t = 0; t < tblNodes.length; t++) {
          const tbl = tblNodes[t];
          const tableEl = document.createElement("table");
          tableEl.style.width = (slideW - 96) + "px";
          tableEl.style.margin = "16px 48px";
          tableEl.style.borderCollapse = "collapse";
          tableEl.style.position = "relative";
          tableEl.style.zIndex = "10";

          const trNodes = tbl.getElementsByTagNameNS("*", "tr");
          for (let r = 0; r < trNodes.length; r++) {
            const trEl = document.createElement("tr");
            const tcNodes = trNodes[r].getElementsByTagNameNS("*", "tc");
            for (let c = 0; c < tcNodes.length; c++) {
              const tdEl = document.createElement(r === 0 ? "th" : "td");
              tdEl.style.border = "1px solid #cbd5e1";
              tdEl.style.padding = "8px 10px";
              tdEl.style.fontSize = "13px";
              tdEl.style.background = r === 0 ? "#1e293b" : "#ffffff";
              tdEl.style.color = r === 0 ? "#ffffff" : "#0f172a";
              tdEl.textContent = (tcNodes[c].textContent || "").trim();
              trEl.appendChild(tdEl);
            }
            tableEl.appendChild(trEl);
          }
          fgDiv.appendChild(tableEl);
          placedElements++;
        }

        const picNodes = xmlDoc.getElementsByTagNameNS("*", "pic");
        for (let p = 0; p < picNodes.length; p++) {
          const pic = picNodes[p];
          const blip = pic.getElementsByTagNameNS("*", "blip")[0];
          const embedId = blip ? (blip.getAttribute("r:embed") || blip.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")) : null;
          const mediaPath = embedId ? relMap[embedId] : null;
          const mediaFile = mediaPath ? zip.file(mediaPath) : null;
          if (mediaFile) {
            try {
              const bytes = mediaFile.asUint8Array();
              const extName = mediaPath.split(".").pop().toLowerCase();
              const mime = extName === "png" ? "image/png" : extName === "svg" ? "image/svg+xml" : "image/jpeg";
              const blob = new Blob([bytes], { type: mime });
              const imgUrl = URL.createObjectURL(blob);

              const xfrm = pic.getElementsByTagNameNS("*", "xfrm")[0];
              const off = xfrm ? xfrm.getElementsByTagNameNS("*", "off")[0] : null;
              const ext = xfrm ? xfrm.getElementsByTagNameNS("*", "ext")[0] : null;

              const img = document.createElement("img");
              img.src = imgUrl;
              img.style.position = "absolute";
              img.style.left = (off ? toPxX(off.getAttribute("x")) : 60) + "px";
              img.style.top = (off ? toPxY(off.getAttribute("y")) : 80) + "px";
              img.style.width = (ext ? Math.max(48, toPxX(ext.getAttribute("cx"))) : 320) + "px";
              img.style.height = (ext ? Math.max(48, toPxY(ext.getAttribute("cy"))) : 220) + "px";
              img.style.objectFit = "contain";
              fgDiv.appendChild(img);
              placedElements++;
            } catch (_) {}
          }
        }

        if (placedElements === 0) {
          const tNodes = xmlDoc.getElementsByTagNameNS("*", "t");
          const fallbackBox = document.createElement("div");
          fallbackBox.style.padding = "48px";
          for (let k = 0; k < tNodes.length; k++) {
            const txt = (tNodes[k].textContent || "").trim();
            if (txt) {
              const p = document.createElement("p");
              p.textContent = txt;
              p.style.fontSize = k === 0 ? "24px" : "16px";
              p.style.fontWeight = k === 0 ? "700" : "400";
              p.style.color = "#0f172a";
              fallbackBox.appendChild(p);
            }
          }
          fgDiv.appendChild(fallbackBox);
        }

        slidesContainer.appendChild(slideDiv);
      });

      stage.appendChild(slidesContainer);
      wrapRenderedSlides();
      return true;
    } catch (e) {
      console.error("Visual OpenXML fallback error:", e);
      return false;
    }
  }

  function loadPptx(docUrl, nightMode) {
    try {
      setNightMode(!!nightMode);
      stage.innerHTML = "";

      $("#slides-stage").pptxToHtml({
        pptxFileUrl: docUrl,
        slidesScale: "",
        slideMode: false,
        keyBoardShortCut: false,
        mediaProcess: true,
        themeProcess: true
      });

      let attempts = 0;
      const poll = setInterval(async () => {
        attempts++;
        const loadingMsg = document.querySelector(".slides-loadnig-msg");
        const slides = stage.querySelectorAll(".slide");
        if ((!loadingMsg && slides.length > 0) || attempts > 35) {
          clearInterval(poll);
          document.querySelectorAll(".slides-loadnig-msg").forEach((el) => el.remove());
          const hasMeaningfulContent = Array.from(slides).some(
            (s) => (s.innerText || s.textContent || "").trim().length > 0 || s.querySelectorAll("img, svg, canvas, div").length > 1
          );
          if (slides.length > 0 && hasMeaningfulContent) {
            wrapRenderedSlides();
          } else {
            const ok = await renderVisualOpenXmlSlides(docUrl);
            if (!ok) {
              notify("onDocumentError", "Unable to parse slides from presentation.");
            }
          }
        }
      }, 80);
    } catch (err) {
      renderVisualOpenXmlSlides(docUrl).then((ok) => {
        if (!ok) {
          notify("onDocumentError", err && err.message ? err.message : String(err));
        }
      });
    }
  }

  async function loadLegacyPpt(docUrl, nightMode) {
    try {
      setNightMode(!!nightMode);
      const res = await fetch(docUrl);
      if (!res.ok) throw new Error("HTTP " + res.status);
      const data = await res.json();

      stage.innerHTML = "";
      state.slideFrames = [];
      const slides = Array.isArray(data.slides) ? data.slides : [];
      const summaries = [];

      const list = slides.length > 0 ? slides : [["No readable text stream extracted from legacy .ppt binary."]];
      list.forEach((lines, idx) => {
        const card = document.createElement("div");
        card.className = "legacy-ppt-slide slide-frame";
        const h3 = document.createElement("h3");
        h3.textContent = "Slide " + (idx + 1);
        card.appendChild(h3);
        (Array.isArray(lines) ? lines : [String(lines)]).forEach((line) => {
          const p = document.createElement("p");
          p.textContent = line;
          card.appendChild(p);
        });
        stage.appendChild(card);
        state.slideFrames.push(card);
        summaries.push({ index: idx + 1, snippet: (Array.isArray(lines) ? lines.join(" ") : "").slice(0, 90) });
      });

      state.totalSlides = state.slideFrames.length;
      state.currentSlide = 1;
      state.singleSlideMode = false;
      notify("onSlideSummaries", JSON.stringify(summaries));
      notify("onDocumentLoaded", state.totalSlides);
      notify("onPageChanged", 1, state.totalSlides);
    } catch (err) {
      notify("onDocumentError", err && err.message ? err.message : String(err));
    }
  }

  function goToSlide(slideNum) {
    if (state.totalSlides <= 0) return;
    const target = Math.max(1, Math.min(state.totalSlides, parseInt(slideNum, 10) || 1));
    state.currentSlide = target;
    layoutSlides();
    if (!state.singleSlideMode && !state.presentationMode) {
      const frame = state.slideFrames[target - 1];
      if (frame) frame.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    notify("onPageChanged", state.currentSlide, state.totalSlides);
  }

  function setPresentationMode(enabled) {
    state.presentationMode = !!enabled;
    document.body.classList.toggle("presentation-mode", state.presentationMode);
    layoutSlides();
  }

  function setSingleSlideMode(enabled) {
    state.singleSlideMode = !!enabled;
    layoutSlides();
  }

  function setZoom(zoomFactor) {
    state.zoom = Math.max(0.5, Math.min(3.0, parseFloat(zoomFactor) || 1.0));
    layoutSlides();
    notify("onZoomChanged", state.zoom);
  }

  function setNightMode(enabled) {
    state.nightMode = !!enabled;
    document.body.classList.toggle("night-mode", state.nightMode);
  }

  function clearHighlights() {
    state.matches.forEach((m) => {
      const p = m.parentNode;
      if (p) {
        p.replaceChild(document.createTextNode(m.textContent), m);
        p.normalize();
      }
    });
    state.matches = [];
    state.activeMatchIdx = -1;
  }

  function search(query) {
    clearHighlights();
    const q = (query || "").trim();
    if (!q) {
      notify("onSearchResult", 0, 0, state.currentSlide);
      return;
    }

    const needle = q.toLowerCase();
    state.slideFrames.forEach((frame, slideIdx) => {
      const walker = document.createTreeWalker(frame, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
          if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
          const p = node.parentNode;
          if (p && (p.classList?.contains("slide-badge") || p.nodeName === "STYLE" || p.nodeName === "SCRIPT")) {
            return NodeFilter.FILTER_REJECT;
          }
          return NodeFilter.FILTER_ACCEPT;
        }
      });

      const nodes = [];
      let n;
      while ((n = walker.nextNode())) nodes.push(n);

      nodes.forEach((node) => {
        const text = node.nodeValue;
        const lower = text.toLowerCase();
        if (!lower.includes(needle)) return;

        const frag = document.createDocumentFragment();
        let lastIdx = 0;
        let idx = lower.indexOf(needle);
        while (idx !== -1) {
          if (idx > lastIdx) frag.appendChild(document.createTextNode(text.slice(lastIdx, idx)));
          const mark = document.createElement("mark");
          mark.className = "docu-match";
          mark.dataset.slide = String(slideIdx + 1);
          mark.textContent = text.slice(idx, idx + q.length);
          frag.appendChild(mark);
          state.matches.push(mark);
          lastIdx = idx + q.length;
          idx = lower.indexOf(needle, lastIdx);
        }
        if (lastIdx < text.length) frag.appendChild(document.createTextNode(text.slice(lastIdx)));
        node.parentNode.replaceChild(frag, node);
      });
    });

    if (state.matches.length > 0) {
      state.activeMatchIdx = 0;
      activateMatch(0);
    } else {
      notify("onSearchResult", 0, 0, state.currentSlide);
    }
  }

  function activateMatch(index) {
    state.matches.forEach((m, i) => m.classList.toggle("active", i === index));
    const target = state.matches[index];
    if (target) {
      const slideNum = parseInt(target.dataset.slide || "1", 10);
      goToSlide(slideNum);
      target.scrollIntoView({ behavior: "smooth", block: "center" });
      notify("onSearchResult", index + 1, state.matches.length, slideNum);
    }
  }

  function stepSearch(delta) {
    if (state.matches.length === 0) return;
    state.activeMatchIdx = (state.activeMatchIdx + delta + state.matches.length) % state.matches.length;
    activateMatch(state.activeMatchIdx);
  }

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

  function extractSlideTextFromFrame(frame) {
    if (!frame) return "";
    const blocks = frame.querySelectorAll(".slide-prgrph, p, h3, tr");
    const lines = [];
    blocks.forEach((b) => {
      const t = (b.innerText || b.textContent || "").replace(/\s+/g, " ").trim();
      if (t) lines.push(t);
    });
    const raw = lines.length > 0 ? lines.join("\n") : (frame.innerText || frame.textContent || "").trim();
    return raw.trim();
  }

  function extractPageText(explicitSlide) {
    const req = parseInt(explicitSlide, 10);
    const targetSlide = Math.max(1, Math.min(state.slideFrames.length || 1, req > 0 ? req : (state.currentSlide || 1)));
    const frame = state.slideFrames[targetSlide - 1];
    const text = extractSlideTextFromFrame(frame);
    notify("onPageTextExtracted", text ? text : "__DOCUVIEW_NO_TEXT_LAYER__");
    return text;
  }

  function extractAllPagesText() {
    const parts = [];
    state.slideFrames.forEach((frame, idx) => {
      const txt = extractSlideTextFromFrame(frame);
      if (txt) parts.push(`--- #${idx + 1} ---\n${txt}`);
    });
    const combined = parts.join("\n\n").trim();
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
    extractPageText(state.currentSlide);
    return "";
  }

  // Horizontal swipe navigation in single-slide / presentation mode (ignore multi-touch & zoomed states)
  document.addEventListener("touchstart", (e) => {
    if (e.touches.length > 1) {
      state.wasMultiTouch = true;
      return;
    }
    if (e.touches.length === 1) {
      state.wasMultiTouch = false;
      state.touchStartX = e.touches[0].clientX;
      state.touchStartY = e.touches[0].clientY;
    }
  }, { passive: true });

  document.addEventListener("touchend", (e) => {
    const vvScale = window.visualViewport ? window.visualViewport.scale : 1.0;
    const hadMultiTouch = state.wasMultiTouch;
    if (e.touches.length === 0) {
      state.wasMultiTouch = false;
    }
    if (hadMultiTouch || vvScale > 1.15) return;
    if ((!state.singleSlideMode && !state.presentationMode) || state.zoom > 1.15) return;
    if (e.changedTouches.length !== 1) return;
    const activeSelection = window.getSelection ? window.getSelection().toString().trim() : "";
    if (activeSelection.length > 0) return;

    const dx = e.changedTouches[0].clientX - state.touchStartX;
    const dy = e.changedTouches[0].clientY - state.touchStartY;
    if (Math.abs(dx) > 55 && Math.abs(dx) > Math.abs(dy) * 1.4) {
      if (dx < 0 && state.currentSlide < state.totalSlides) {
        goToSlide(state.currentSlide + 1);
      } else if (dx > 0 && state.currentSlide > 1) {
        goToSlide(state.currentSlide - 1);
      }
    }
  }, { passive: true });

  window.addEventListener("resize", () => layoutSlides());

  window.DocuPptx = {
    loadPptx,
    loadLegacyPpt,
    goToSlide,
    nextSlide: () => goToSlide(state.currentSlide + 1),
    prevSlide: () => goToSlide(state.currentSlide - 1),
    setPresentationMode,
    setSingleSlideMode,
    setZoom,
    setNightMode,
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
      loadLegacyPpt(docParam, params.get("night") === "1");
    } else {
      loadPptx(docParam, params.get("night") === "1");
    }
  }
})();
