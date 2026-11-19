package com.example.webview

import org.json.JSONObject

object GameScriptInjector {

    fun quote(str: String): String {
        return "\"" + str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b") + "\""
    }

    fun buildClearStorageScript(): String {
        return """
        try {
            localStorage.clear();
            sessionStorage.clear();
        } catch(e) {}
        """.trimIndent()
    }

    fun buildAdjustViewportScript(): String {
        return """
        try {
            if (!document.getElementById('__vl_mobile_opt_style') && document.head) {
                const st = document.createElement('style');
                st.id = '__vl_mobile_opt_style';
                st.textContent = `
                    *, *::before, *::after {
                        backdrop-filter: none !important;
                        -webkit-backdrop-filter: none !important;
                        box-shadow: none !important;
                        text-shadow: none !important;
                    }
                    .floating-text, .damage-text, .damage-number, .particle, .fx {
                        animation: none !important;
                        transition: none !important;
                        filter: none !important;
                    }
                    input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                        min-width: 44px !important;
                    }
                    @media (max-width: 280px) {
                        input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                            min-width: 36px !important;
                            width: 100% !important;
                        }
                    }
                `;
                document.head.appendChild(st);
            }
            window.dispatchEvent(new Event('resize'));
            if (document.body) {
                document.body.style.width = '100%';
                document.body.style.height = '100%';
            }
        } catch(e) {}
        """.trimIndent()
    }

    fun buildAutoLoginStepScript(
        tabIndex: Int,
        username: String,
        password: String
    ): String {
        val safeUserJson = quote(username)
        val safePassJson = quote(password)

        return """
        (function(username, password) {
            try {
                const tabIdx = $tabIndex;

                try {
                    if (!document.getElementById('__vl_mobile_opt_style') && document.head) {
                        const st = document.createElement('style');
                        st.id = '__vl_mobile_opt_style';
                        st.textContent = `
                            * {
                                backdrop-filter: none !important;
                                -webkit-backdrop-filter: none !important;
                            }
                            input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                                min-width: 44px !important;
                            }
                            @media (max-width: 280px) {
                                input:not([type="checkbox"]):not([type="radio"]):not([type="button"]):not([type="submit"]) {
                                    min-width: 36px !important;
                                    width: 100% !important;
                                }
                            }
                        `;
                        document.head.appendChild(st);
                    }
                } catch(e) {}

                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function isVisible(el) {
                    if (!el || !el.isConnected) return false;
                    if ((el.type || '').toLowerCase() === 'hidden') return false;
                    let cur = el;
                    while (cur && cur !== document.body && cur !== document.documentElement) {
                        if (cur.hidden === true) return false;
                        try {
                            const style = window.getComputedStyle(cur);
                            if (style && (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || parseFloat(style.opacity) === 0)) {
                                return false;
                            }
                        } catch(e) {}
                        cur = cur.parentElement;
                    }
                    const tag = (el.tagName || '').toUpperCase();
                    const isFormOrBtn = (tag === 'INPUT' || tag === 'BUTTON' || tag === 'SELECT' || tag === 'TEXTAREA' || el.getAttribute('role') === 'button');
                    try {
                        const rect = el.getBoundingClientRect();
                        if (isFormOrBtn) {
                            if (rect.height > 0 || rect.width > 0 || el.offsetParent !== null) return true;
                            if (el.parentElement) {
                                const pRect = el.parentElement.getBoundingClientRect();
                                return pRect.height > 0 || pRect.width > 0;
                            }
                        }
                        return rect.width > 1 && rect.height > 1;
                    } catch(e) {
                        return el.offsetParent !== null;
                    }
                }

                function realClick(el) {
                    if (!el) return;
                    const target = el.closest('button, [role="button"], a, input[type="button"], input[type="submit"]') || el;
                    try {
                        target.disabled = false;
                        target.removeAttribute('disabled');
                        if (target.classList) target.classList.remove('disabled');
                    } catch(e) {}
                    try {
                        const rect = target.getBoundingClientRect();
                        const cx = rect.left + Math.max(2, rect.width / 2);
                        const cy = rect.top + Math.max(2, rect.height / 2);
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(type => {
                            try {
                                target.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof target.click === 'function') {
                        try { target.click(); } catch(e) {}
                    } else {
                        try {
                            target.dispatchEvent(new MouseEvent('click', {
                                view: window, bubbles: true, cancelable: true
                            }));
                        } catch(e) {}
                    }
                }

                function forceFillReactInput(inputEl, textValue) {
                    if (!inputEl || textValue === undefined || textValue === null) return;
                    const strVal = String(textValue);
                    const prevInputMode = inputEl.getAttribute('inputmode');
                    try {
                        inputEl.setAttribute('inputmode', 'none');
                    } catch(e) {}

                    try { inputEl.focus({ preventScroll: true }); } catch(e) {
                        try { inputEl.focus(); } catch(e2) {}
                    }

                    if (inputEl._valueTracker) {
                        try { inputEl._valueTracker.setValue(''); } catch(e) {}
                    }

                    let nativeSetter = null;
                    try {
                        nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
                        if (!nativeSetter) {
                            nativeSetter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(inputEl), 'value')?.set;
                        }
                    } catch(e) {}

                    if (nativeSetter) {
                        try { nativeSetter.call(inputEl, strVal); } catch(e) { inputEl.value = strVal; }
                    } else {
                        inputEl.value = strVal;
                    }

                    try { inputEl.dispatchEvent(new Event('focus', { bubbles: true })); } catch(e) {}
                    try { inputEl.dispatchEvent(new Event('keydown', { bubbles: true })); } catch(e) {}
                    try {
                        if (typeof InputEvent === 'function') {
                            inputEl.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true, data: strVal, inputType: 'insertText' }));
                        } else {
                            inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                        }
                    } catch(e) {
                        try { inputEl.dispatchEvent(new Event('input', { bubbles: true })); } catch(e2) {}
                    }
                    try { inputEl.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                    try { inputEl.dispatchEvent(new Event('keyup', { bubbles: true })); } catch(e) {}
                    try { inputEl.dispatchEvent(new Event('blur', { bubbles: true })); } catch(e) {}

                    setTimeout(function() {
                        try {
                            if (prevInputMode === null) inputEl.removeAttribute('inputmode');
                            else inputEl.setAttribute('inputmode', prevInputMode);
                        } catch(e) {}
                    }, 600);
                }

                function getOwnInputMeta(inp) {
                    const parts = [
                        inp.placeholder || '',
                        inp.name || '',
                        inp.id || '',
                        inp.getAttribute('aria-label') || '',
                        inp.getAttribute('autocomplete') || '',
                        inp.title || ''
                    ];
                    if (inp.labels && inp.labels.length > 0) {
                        for (const lb of Array.from(inp.labels)) {
                            parts.push(lb.textContent || lb.innerText || '');
                        }
                    }
                    const closestLabel = inp.closest('label');
                    if (closestLabel) {
                        parts.push(closestLabel.textContent || closestLabel.innerText || '');
                    }
                    const prev = inp.previousElementSibling;
                    if (prev) {
                        const prevTxt = (prev.textContent || prev.innerText || '').replace(/\s+/g, ' ').trim();
                        if (prevTxt.length <= 50) parts.push(prevTxt);
                    }
                    const parent = inp.parentElement;
                    if (parent) {
                        const inputsInParent = parent.querySelectorAll('input').length;
                        if (inputsInParent === 1) {
                            const pTxt = (parent.textContent || parent.innerText || '').replace(/\s+/g, ' ').trim();
                            if (pTxt.length <= 70) {
                                parts.push(pTxt);
                            }
                        }
                    }
                    return noAccent(parts.join(' '));
                }

                function isServerField(inp) {
                    if ((inp.type || '').toLowerCase() === 'password') return false;
                    if (inp.readOnly || inp.disabled) return true;
                    const valNA = noAccent(inp.value || '');
                    if (valNA.includes('vo lam idle')) return true;
                    const meta = getOwnInputMeta(inp);
                    if (meta.includes('tai khoan') || meta.includes('mat ma') || meta.includes('mat khau') || meta.includes('username') || meta.includes('password')) {
                        return false;
                    }
                    return meta.includes('may chu') || meta.includes('server');
                }

                function isConfirmField(inp) {
                    const meta = getOwnInputMeta(inp);
                    return meta.includes('xac nhan') || meta.includes('nhap lai') || meta.includes('confirm') || meta.includes('retype');
                }

                function isPasswordField(inp) {
                    if (isServerField(inp)) return false;
                    if ((inp.type || '').toLowerCase() === 'password') return true;
                    const meta = getOwnInputMeta(inp);
                    return meta.includes('mat ma') || meta.includes('mat khau') || meta.includes('password') || meta.includes('pass');
                }

                function isUsernameField(inp) {
                    if (isServerField(inp) || isPasswordField(inp) || isConfirmField(inp)) return false;
                    const meta = getOwnInputMeta(inp);
                    return meta.includes('tai khoan') || meta.includes('ten dang nhap') || meta.includes('ten nhan vat') ||
                           meta.includes('username') || meta.includes('account') || meta.includes('user');
                }

                const CHAT_COMPOSER_KEYWORDS = ['nhan tin', 'tin nhan', 'chat', 'nhap tin', 'noi dung', 'message', 'comment', 'mat ngu'];

                function isChatOrComposer(inp) {
                    const id = (inp.id || '').toLowerCase();
                    if (id === 'u20txt' || id === 'chattxt' || id === 'hhchtxt') return true;
                    const meta = getOwnInputMeta(inp);
                    for (const kw of CHAT_COMPOSER_KEYWORDS) {
                        if (meta.includes(kw)) return true;
                    }
                    return false;
                }

                const NON_AUTH_IDS = ['gcin', 'nmto', 'nmin', 'nmnote', 'ptname', 'ptto', 'ptnote',
                                      'hhname', 'hhmnname', 'hhdenname', 'ncname',
                                      'mku', 'mkm', 'mkc', 'mkp1', 'mkp2', 'bstb', 'bstuyen',
                                      'prq', 'hfq', 'nkq', 'petnmi', 'nau', 'nap'];

                function authGateRoot() {
                    return document.querySelector('#modal .dn20[data-dn20], .dn20[data-dn20]');
                }

                function authScopeRoot() {
                    const gate = authGateRoot();
                    if (gate) return gate;
                    if (document.getElementById('naP') || document.getElementById('naIn')) return null;
                    return document;
                }

                const allClickables = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], [role="button"], a, div, span, p, label'));

                function getVisibleEditableInputs() {
                    const scope = authScopeRoot();
                    if (!scope) return [];
                    return Array.from(document.querySelectorAll('input')).filter(inp => {
                        if (scope !== document && !scope.contains(inp)) return false;
                        if (NON_AUTH_IDS.indexOf((inp.id || '').toLowerCase()) >= 0) return false;
                        if (inp.classList && inp.classList.contains('bhin')) return false;
                        const t = (inp.type || 'text').toLowerCase();
                        if (['hidden', 'checkbox', 'radio', 'button', 'submit', 'reset', 'image', 'file', 'range', 'color', 'number', 'search', 'date', 'time', 'datetime-local', 'month', 'week'].includes(t)) {
                            return false;
                        }
                        if (!isVisible(inp)) return false;
                        if (isServerField(inp)) return false;
                        if (isChatOrComposer(inp)) return false;
                        return true;
                    });
                }

                function byId(id) {
                    return document.getElementById(id);
                }

                function gateLoginVisible() {
                    const gate = authGateRoot();
                    if (!gate || !isVisible(gate)) return false;
                    const u = byId('ngU');
                    const p = byId('ngP');
                    return !!((u && isVisible(u)) || (p && isVisible(p)));
                }

                function gateMenuVisible() {
                    const b = byId('ngStart');
                    return !!(b && isVisible(b));
                }

                function charSelectVisible() {
                    const c = byId('csel');
                    return !!(c && isVisible(c));
                }

                function inGameNow() {
                    if (gateLoginVisible() || gateMenuVisible() || charSelectVisible()) return false;
                    const msw = byId('modeSw');
                    if (msw && !msw.classList.contains('hidden')) return true;
                    const body = document.body;
                    if (body && (body.classList.contains('u20') || body.classList.contains('u2m'))) return true;
                    const stage = byId('stageLbl');
                    if (stage && (stage.textContent || '').replace(/\s+/g, '') !== '') return true;
                    const logBox = byId('logBox');
                    if (logBox && logBox.children && logBox.children.length > 0) return true;
                    return false;
                }

                function findGateSubmitButton() {
                    const gate = authGateRoot();
                    const btn = byId('ngIn');
                    if (btn && isVisible(btn) && (!gate || gate.contains(btn))) return btn;
                    return null;
                }

                function ensureRememberAccount() {
                    const rem = byId('ngRem');
                    if (rem) {
                        if (!rem.classList.contains('on')) {
                            try { rem.click(); } catch(e) {}
                        }
                        return;
                    }
                    const visibleCheckboxes = Array.from(document.querySelectorAll('input[type="checkbox"]')).filter(isVisible);
                    for (const cb of visibleCheckboxes) {
                        const lb = cb.closest('label');
                        const nextEl = cb.nextElementSibling;
                        const parentEl = cb.parentElement;
                        const singleCbInParent = parentEl && parentEl.querySelectorAll('input[type="checkbox"]').length === 1;
                        const cbText = noAccent(
                            (lb ? (lb.textContent || lb.innerText || '') : '') + ' ' +
                            (nextEl ? (nextEl.textContent || nextEl.innerText || '') : '') + ' ' +
                            (singleCbInParent ? (parentEl.textContent || parentEl.innerText || '') : '')
                        );

                        if (cbText.includes('hien mat') || cbText.includes('hien thi mat') || cbText.includes('show pass')) {
                            if (cb.checked) {
                                try { cb.click(); } catch(e) {}
                            }
                            continue;
                        }

                        if (cbText.includes('nho tai khoan') || cbText.includes('ghi nho') || cbText.includes('remember')) {
                            if (!cb.checked) {
                                try { cb.click(); } catch(e) {}
                                if (!cb.checked) {
                                    cb.checked = true;
                                    try { cb.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                                }
                            }
                        }
                    }
                }

                function isBtnBusyOrDisabled(btn) {
                    if (!btn) return false;
                    try {
                        if (btn.disabled || btn.getAttribute('aria-disabled') === 'true' ||
                            (btn.classList && (btn.classList.contains('disabled') || btn.classList.contains('loading')))) {
                            return true;
                        }
                        const t = noAccent(btn.innerText || btn.textContent || btn.value || '');
                        if (t.includes('dang ket noi') || t.includes('dang dang nhap') ||
                            t.includes('dang xu ly') || t.includes('dang tai') ||
                            t.includes('connecting') || t.includes('loading')) {
                            return true;
                        }
                    } catch(e) {}
                    return false;
                }

                function tryEnterCharacterSelect() {
                    const csel = byId('csel');
                    if (!csel || !isVisible(csel)) return null;
                    const go = byId('csGo');
                    if (!go || !isVisible(go) || isBtnBusyOrDisabled(go)) {
                        if (Date.now() - (window.__lastCharPickTime || 0) >= 2200) {
                            const cards = csel.querySelectorAll('.cs-slot[data-i]:not(.empty)');
                            for (let i = 0; i < cards.length; i++) {
                                const card = cards[i];
                                if (card.classList.contains('on')) continue;
                                window.__lastCharPickTime = Date.now();
                                realClick(card);
                                return 'CLICKED_CHARACTER_CARD';
                            }
                        }
                        return 'WAITING_CHAR_SELECT';
                    }
                    if (Date.now() - (window.__lastCharGoTime || 0) < 4500) return 'WAITING_CHAR_SELECT';

                    const onCard = csel.querySelector('.cs-slot.on');
                    if (!onCard && Date.now() - (window.__lastCharPickTime || 0) >= 2200) {
                        const firstCard = csel.querySelector('.cs-slot[data-i]:not(.empty)');
                        if (firstCard) {
                            window.__lastCharPickTime = Date.now();
                            realClick(firstCard);
                            return 'CLICKED_CHARACTER_CARD';
                        }
                    }

                    window.__lastCharGoTime = Date.now();
                    setTimeout(function() {
                        try {
                            const btn = byId('csGo');
                            if (btn && !isBtnBusyOrDisabled(btn) && isVisible(btn)) realClick(btn);
                        } catch(e) {}
                    }, 350);

                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "Đã chọn nhân vật và ấn [Xác Định] vào game");
                    }
                    return "CLICKED_CHARACTER_SELECT";
                }

                function findBestButtonByText(matcherFn) {
                    let bestEl = null;
                    let bestScore = -999;

                    for (const el of allClickables) {
                        const rawContent = (el.textContent || el.value || '').replace(/\s+/g, ' ').trim();
                        if (!rawContent || rawContent.length > 45) continue;
                        const contentNA = noAccent(rawContent);

                        let matchScore = matcherFn(contentNA, rawContent, el);
                        if (matchScore <= 0) {
                            const rawInner = (el.textContent || el.value || '').replace(/\s+/g, ' ').trim();
                            if (rawInner && rawInner.length <= 45 && rawInner !== rawContent) {
                                matchScore = matcherFn(noAccent(rawInner), rawInner, el);
                            }
                        }
                        if (matchScore <= 0) continue;

                        if (!isVisible(el)) continue;

                        let score = matchScore;
                        const tag = (el.tagName || '').toUpperCase();
                        if (tag === 'BUTTON' || tag === 'INPUT') score += 60;
                        else if (el.getAttribute('role') === 'button' || tag === 'A') score += 45;
                        else if (el.closest('button, [role="button"], a')) score += 35;

                        if (el.children && el.children.length === 0) score += 20;
                        else if (el.children && el.children.length <= 4) score += 10;

                        if (score > bestScore) {
                            bestScore = score;
                            bestEl = el;
                        }
                    }
                    return bestEl;
                }

                function findLoginSubmitButton(referenceInput) {
                    const refRect = referenceInput ? referenceInput.getBoundingClientRect() : null;
                    return findBestButtonByText(function(txtNA, rawTxt, el) {
                        if (txtNA === 'huy' || txtNA.includes('dang ky') || txtNA.includes('quen mat') ||
                            txtNA.includes('hien mat') || txtNA.includes('nho tai khoan') ||
                            txtNA.includes('tao nhan vat') || txtNA.includes('chua co tai khoan') ||
                            txtNA.includes('bat dau tro choi')) {
                            return -1;
                        }

                        let base = 0;
                        if (txtNA === 'xac dinh') base = 200;
                        else if (txtNA === 'xac nhan' || txtNA === 'dang nhap' || txtNA === 'login') base = 120;
                        else if (txtNA.startsWith('xac dinh') || txtNA.startsWith('dang nhap')) base = 90;
                        else return -1;

                        if (referenceInput && refRect && refRect.height > 0) {
                            try {
                                const btnRect = el.getBoundingClientRect();
                                if (btnRect.height > 0 && btnRect.top >= refRect.top - 20) {
                                    base += 80;
                                }
                            } catch(e) {}
                        }
                        return base;
                    });
                }

                function tryFillAndSubmitLoginModal() {
                    const editableInputs = getVisibleEditableInputs();
                    if (editableInputs.length === 0) return null;

                    let passInput = null;
                    let userInput = null;

                    for (const inp of editableInputs) {
                        if ((inp.type || '').toLowerCase() === 'password' && !isConfirmField(inp)) {
                            passInput = inp;
                            break;
                        }
                    }
                    if (!passInput) {
                        for (const inp of editableInputs) {
                            if (isPasswordField(inp) && !isConfirmField(inp)) {
                                passInput = inp;
                                break;
                            }
                        }
                    }
                    if (!passInput && editableInputs.length === 2 && !isConfirmField(editableInputs[1]) && authGateRoot()) {
                        userInput = editableInputs[0];
                        passInput = editableInputs[1];
                    }

                    if (!passInput) return null;

                    if (!username || !password) {
                        if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                            window.AndroidBridge.onGameLog(tabIdx, "Chờ nhập TK & MK ở nút 🔑 TK");
                        }
                        return "NO_CREDENTIALS";
                    }

                    if (!userInput) {
                        for (const inp of editableInputs) {
                            if (inp === passInput) continue;
                            if (isUsernameField(inp)) {
                                userInput = inp;
                                break;
                            }
                        }
                    }
                    if (!userInput) {
                        const passIdx = editableInputs.indexOf(passInput);
                        if (passIdx > 0) {
                            for (let i = passIdx - 1; i >= 0; i--) {
                                const candidate = editableInputs[i];
                                if (!isConfirmField(candidate) && !isPasswordField(candidate)) {
                                    userInput = candidate;
                                    break;
                                }
                            }
                        }
                    }
                    if (!userInput) {
                        for (const inp of editableInputs) {
                            if (inp !== passInput && !isConfirmField(inp)) {
                                userInput = inp;
                                break;
                            }
                        }
                    }

                    const nowMs = Date.now();
                    const timeSinceLastSubmit = nowMs - (window.__lastLoginSubmitTime || 0);
                    const valuesAlreadyMatch = (!userInput || userInput.value === username) && (passInput.value === password);
                    const initialSubmitBtn = findGateSubmitButton() || findLoginSubmitButton(passInput);

                    if (isBtnBusyOrDisabled(initialSubmitBtn)) {
                        return "WAITING_LOGIN_RESPONSE";
                    }

                    if (valuesAlreadyMatch && timeSinceLastSubmit < 4500) {
                        return "WAITING_LOGIN_RESPONSE";
                    }

                    window.__lastLoginSubmitTime = nowMs;

                    if (userInput && userInput.value !== username) {
                        forceFillReactInput(userInput, username);
                    }
                    if (passInput.value !== password) {
                        forceFillReactInput(passInput, password);
                    }

                    ensureRememberAccount();

                    function confirmAndClickSubmit() {
                        try {
                            if (!passInput || !passInput.isConnected || !isVisible(passInput)) return;
                            if (userInput && userInput.isConnected && userInput.value !== username) {
                                forceFillReactInput(userInput, username);
                            }
                            if (passInput.value !== password) {
                                forceFillReactInput(passInput, password);
                            }
                            const submitBtn = findGateSubmitButton() || findLoginSubmitButton(passInput);
                            if (submitBtn) {
                                if (isBtnBusyOrDisabled(submitBtn)) return;
                                window.__lastLoginSubmitTime = Date.now();
                                realClick(submitBtn);
                                if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                                    window.AndroidBridge.onGameLog(tabIdx, "Đã ấn [Xác Định] đăng nhập: " + username);
                                }
                                return;
                            }
                            window.__lastLoginSubmitTime = Date.now();
                            try {
                                passInput.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));
                                passInput.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));
                            } catch(e) {}
                            if (passInput.form) {
                                try {
                                    if (typeof passInput.form.requestSubmit === 'function') {
                                        passInput.form.requestSubmit();
                                    } else {
                                        passInput.form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                                    }
                                } catch(e) {}
                            }
                        } catch(e) {}
                    }

                    setTimeout(confirmAndClickSubmit, 320);

                    return "FILLED_AND_CLICKED_XAC_DINH";
                }

                const playHereBtn = findBestButtonByText(function(txtNA, rawTxt) {
                    if (rawTxt === 'Chơi ở đây' || rawTxt === 'Chơi ở tab này' ||
                        rawTxt.includes('Chơi ở đây') || rawTxt.includes('Chơi ở tab này') ||
                        rawTxt.includes('Tiếp tục ở đây') || rawTxt.includes('Kết nối lại')) {
                        return 100;
                    }
                    return -1;
                });
                if (playHereBtn && !isBtnBusyOrDisabled(playHereBtn)) {
                    if (Date.now() - (window.__lastDialogActionTime || 0) < 2500) {
                        return "WAITING_LOGIN_RESPONSE";
                    }
                    window.__lastDialogActionTime = Date.now();
                    realClick(playHereBtn);
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "⚡ Đã tự động chọn [Chơi ở đây] để tiếp tục chạy game");
                    }
                    return "CLICKED_PLAY_HERE";
                }

                const agreeBtn = findBestButtonByText(function(txtNA) {
                    if (txtNA === 'toi dong tinh' || txtNA === 'dong tinh' || txtNA === 'toi da hieu') {
                        return 100;
                    }
                    return -1;
                });
                if (agreeBtn && !isBtnBusyOrDisabled(agreeBtn)) {
                    if (Date.now() - (window.__lastDialogActionTime || 0) < 2500) {
                        return "WAITING_LOGIN_RESPONSE";
                    }
                    window.__lastDialogActionTime = Date.now();
                    realClick(agreeBtn);
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "Đã nhấn [Tôi Đồng Tình]");
                    }
                    return "CLICKED_AGREE";
                }

                const loginModalResult = tryFillAndSubmitLoginModal();
                if (loginModalResult !== null) {
                    return loginModalResult;
                }

                const charSelectResult = tryEnterCharacterSelect();
                if (charSelectResult !== null) {
                    return charSelectResult;
                }

                const charSelectConfirmBtn = findBestButtonByText(function(txtNA, rawTxt, el) {
                    if (txtNA === 'xac dinh' || txtNA === 'vao game' || txtNA === 'bat dau') {
                        const isLoginModal = el.closest('form') || (el.parentElement && el.parentElement.querySelector && el.parentElement.querySelector('input[type="password"]'));
                        if (!isLoginModal) return 130;
                    }
                    return -1;
                });

                if (charSelectConfirmBtn && !isBtnBusyOrDisabled(charSelectConfirmBtn) && getVisibleEditableInputs().length === 0) {
                    if (Date.now() - (window.__lastCharGoTime || 0) < 4500) {
                        return 'WAITING_CHAR_SELECT';
                    }
                    window.__lastCharGoTime = Date.now();
                    const sects = ['Thiếu Lâm', 'Thiên Vương', 'Đường Môn', 'Ngũ Độc', 'Nga Mi', 'Thúy Yên', 'Cái Bang', 'Thiên Nhẫn', 'Võ Đang', 'Côn Lôn', 'Đoàn Thị'];
                    let charCard = null;
                    for (const el of allClickables) {
                        if (!isVisible(el)) continue;
                        const txt = (el.innerText || '');
                        if (txt.includes('Cấp') || txt.includes('Lv') || sects.some(s => txt.includes(s))) {
                            const rect = el.getBoundingClientRect();
                            if (rect.height > 20 && rect.width > 50) {
                                charCard = el;
                                break;
                            }
                        }
                    }

                    if (charCard) {
                        realClick(charCard);
                    }

                    setTimeout(function() {
                        try {
                            if (charSelectConfirmBtn.isConnected && !isBtnBusyOrDisabled(charSelectConfirmBtn)) {
                                realClick(charSelectConfirmBtn);
                            }
                        } catch(e) {}
                        if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                            window.AndroidBridge.onGameLog(tabIdx, "Đã chọn nhân vật và ấn vào game");
                        }
                    }, 350);

                    return "CLICKED_CHARACTER_SELECT";
                }

                const startGameBtn = findBestButtonByText(function(txtNA) {
                    if (txtNA === 'bat dau tro choi') return 120;
                    if (txtNA.includes('bat dau tro choi')) return 90;
                    return -1;
                });

                if (startGameBtn && !isBtnBusyOrDisabled(startGameBtn)) {
                    if (Date.now() - (window.__lastStartBtnTime || 0) < 3000) {
                        return "WAITING_LOGIN_RESPONSE";
                    }
                    window.__lastStartBtnTime = Date.now();
                    realClick(startGameBtn);
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, "Đã nhấn [Bắt Đầu Trò Chơi] -> Mở khung Đăng Nhập");
                    }
                    setTimeout(function() {
                        try { tryFillAndSubmitLoginModal(); } catch(e) {}
                    }, 750);
                    return "CLICKED_BAT_DAU_TRO_CHOI";
                }

                const otherOpenBtn = findBestButtonByText(function(txtNA) {
                    if (txtNA === 'vao game' || txtNA === 'vao giang ho' || txtNA === 'bat dau' ||
                        txtNA === 'vao tro choi' || txtNA === 'choi ngay') {
                        return 80;
                    }
                    return -1;
                });
                if (otherOpenBtn && !isBtnBusyOrDisabled(otherOpenBtn)) {
                    if (Date.now() - (window.__lastStartBtnTime || 0) < 3000) {
                        return "WAITING_LOGIN_RESPONSE";
                    }
                    window.__lastStartBtnTime = Date.now();
                    realClick(otherOpenBtn);
                    setTimeout(function() {
                        try { tryFillAndSubmitLoginModal(); } catch(e) {}
                    }, 750);
                    return "CLICKED_OPEN_LOGIN";
                }

                if (inGameNow()) {
                    if (window.AndroidBridge && window.AndroidBridge.onInGameDetected) {
                        window.AndroidBridge.onInGameDetected(tabIdx);
                    }
                    return "IN_GAME";
                }

                if (!byId('modeSw') && !byId('stageLbl') && !byId('csel') && !authGateRoot()) {
                    const bodyText = document.body ? (document.body.innerText || '') : '';
                    const hasBattleMenu = (bodyText.includes('Hành trang') && (bodyText.includes('Kỹ năng') || bodyText.includes('Võ công') || bodyText.includes('Hệ thống'))) ||
                                         bodyText.includes('Chế độ :') ||
                                         bodyText.includes('Chế độ:') ||
                                         bodyText.includes('Chế Độ :') ||
                                         bodyText.includes('Chế Độ:');
                    if (hasBattleMenu) {
                        if (window.AndroidBridge && window.AndroidBridge.onInGameDetected) {
                            window.AndroidBridge.onInGameDetected(tabIdx);
                        }
                        return "IN_GAME";
                    }
                }

                return "WAITING_SCREEN";
            } catch (err) {
                if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                    window.AndroidBridge.onGameLog($tabIndex, "Lỗi script: " + err.message);
                }
                return "ERROR: " + err.message;
            }
        })($safeUserJson, $safePassJson);
        """.trimIndent()
    }

    fun buildPeriodicWatchdogAndScanScript(tabIndex: Int): String {
        return """
        (function() {
            try {
                const tabIdx = $tabIndex;
                
                const activeEl = document.activeElement;
                if (activeEl) {
                    const tag = (activeEl.tagName || '').toUpperCase();
                    if (tag === 'SELECT' || tag === 'INPUT' || tag === 'TEXTAREA') {
                        return "USER_IS_INTERACTING";
                    }
                }
                
                if (window.__isUserTouching) {
                    return "USER_IS_TOUCHING_FROM_KOTLIN";
                }

                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function isVisible(el) {
                    if (!el || !el.isConnected) return false;
                    if ((el.type || '').toLowerCase() === 'hidden') return false;
                    const tag = (el.tagName || '').toUpperCase();
                    try {
                        if (el.getClientRects().length === 0 && tag !== 'BODY') {
                            return false;
                        }
                    } catch(e) {}
                    let cur = el;
                    while (cur && cur !== document.body && cur !== document.documentElement) {
                        if (cur.hidden === true) return false;
                        try {
                            const style = window.getComputedStyle(cur);
                            if (style && (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse' || parseFloat(style.opacity) === 0)) {
                                return false;
                            }
                        } catch(e) {}
                        cur = cur.parentElement;
                    }
                    const isFormOrBtn = (tag === 'INPUT' || tag === 'BUTTON' || tag === 'SELECT' || tag === 'TEXTAREA' || el.getAttribute('role') === 'button');
                    try {
                        const rect = el.getBoundingClientRect();
                        if (isFormOrBtn) {
                            if (rect.height > 0 || rect.width > 0 || el.offsetParent !== null) return true;
                            if (el.parentElement) {
                                const pRect = el.parentElement.getBoundingClientRect();
                                return pRect.height > 0 || pRect.width > 0;
                            }
                        }
                        return rect.width > 1 && rect.height > 1;
                    } catch(e) {
                        return el.offsetParent !== null;
                    }
                }

                function realClick(el) {
                    if (!el) return;
                    const target = el.closest('button, [role="button"], a, input[type="button"], input[type="submit"]') || el;
                    try {
                        const rect = target.getBoundingClientRect();
                        const cx = rect.left + Math.max(2, rect.width / 2);
                        const cy = rect.top + Math.max(2, rect.height / 2);
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(type => {
                            try {
                                target.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof target.click === 'function') {
                        try { target.click(); } catch(e) {}
                    }
                }

                function cleanText(txt) {
                    if (!txt) return "";
                    return txt.replace(/\n+/g, ' ').replace(/\s+/g, ' ').trim();
                }

                if (!window.__vlScanSt) {
                    window.__vlScanSt = { t: 0, st: null };
                    window.addEventListener('message', function (msgEvt) {
                        try {
                            if (msgEvt.source !== window) return;
                            const d = msgEvt.data;
                            if (!d || d.vlm !== 'st' || !d.st || typeof d.st !== 'object') return;
                            window.__vlScanSt.st = d.st;
                            window.__vlScanSt.t = Date.now();
                        } catch (errSt) {}
                    });
                }
                const stAge = window.__vlScanSt ? (Date.now() - (window.__vlScanSt.t || 0)) : 1e9;
                const stp = (window.__vlScanSt && stAge < 15000) ? window.__vlScanSt.st : null;
                const stInGame = !!(stp && stp.o === 'game');
                const stOutGame = !!(stp && stAge < 5000 && (stp.o === 'dangnhap' || stp.o === 'menu'));
                const modalEl = document.getElementById('modal');
                const isStealth = !!(document.documentElement && document.documentElement.classList && document.documentElement.classList.contains('__vlm-stealth'));
                const modalOpen = !isStealth && !!(modalEl && !modalEl.classList.contains('hidden'));

                // Tránh đọc document.body.innerText (gây layout reflow nặng) khi stp đã xác nhận in-game
                // và không có modal bật; chỉ đọc nội dung #modal hoặc khi chưa vào game.
                let bodyText = '';
                if (!stInGame) {
                    bodyText = document.body ? (document.body.innerText || '') : '';
                    for (const chatId of ['chatLines', 'u20lines', 'u20log', 'chatBox', 'logBox']) {
                        const cel = document.getElementById(chatId);
                        if (cel && cel.innerText) bodyText = bodyText.split(cel.innerText).join(' ');
                    }
                } else if (modalOpen) {
                    bodyText = modalEl.innerText || modalEl.textContent || '';
                }
                const bodyNA = bodyText ? noAccent(bodyText) : '';
                const allElements = Array.from(
                    document.querySelectorAll(
                        (!stInGame || modalOpen)
                            ? 'button, [role="button"], a, div, span, p, label'
                            : 'button, [role="button"], a'
                    )
                );

                for (const el of allElements) {
                    const txt = (el.textContent || el.innerText || '').replace(/\s+/g, ' ').trim();
                    if (!txt || txt.length > 35) continue;
                    const isRetryBtn = (txt === 'Thử lại' || txt === 'Kết nối lại' || txt === 'Tải lại');
                    if (txt === 'Chơi ở đây' || txt === 'Chơi ở tab này' ||
                        txt.includes('Chơi ở đây') || txt.includes('Chơi ở tab này') ||
                        txt.includes('Tiếp tục ở đây') || txt.includes('Kết nối lại') ||
                        isRetryBtn) {
                        if (!isVisible(el)) continue;
                        if (isRetryBtn) {
                            const nowRt = Date.now();
                            let lastRt = 0;
                            try { lastRt = parseInt(sessionStorage.getItem('__vl_retry_at') || '0', 10) || 0; } catch(eRt) {}
                            if (nowRt - lastRt < 25000) continue;
                            try { sessionStorage.setItem('__vl_retry_at', String(nowRt)); } catch(eRt2) {}
                        }
                        realClick(el);
                        if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                            window.AndroidBridge.onGameLog(tabIdx, "⚡ Đã tự bấm [" + txt + "] tiếp tục phiên chơi");
                        }
                        return "CLICKED_PLAY_HERE";
                    }
                }

                if (bodyText && (bodyText.includes('Tài khoản đang mở ở nơi khác') || bodyText.includes('đang mở ở máy / tab khác') || bodyNA.includes('mat ket noi') || bodyNA.includes('ket noi bi ngat') || bodyNA.includes('phien het han'))) {
                    for (const el of allElements) {
                        const txt = (el.textContent || el.innerText || '').replace(/\s+/g, ' ').trim();
                        if (!txt || txt.length > 35) continue;
                        if (txt.includes('Chơi ở đây') || txt.includes('Chơi ở tab') || txt.includes('Tiếp tục') || txt.includes('Kết nối lại') || txt === 'Thử lại' || txt === 'Đồng ý') {
                            if (!isVisible(el)) continue;
                            realClick(el);
                            if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                                window.AndroidBridge.onGameLog(tabIdx, "⚡ Khôi phục phiên: Đã chọn [" + txt + "]");
                            }
                            return "CLICKED_PLAY_HERE";
                        }
                    }
                }

                function visById(id) {
                    const e = document.getElementById(id);
                    return !!(e && isVisible(e));
                }
                const gateMarkup = !!(document.getElementById('ngStart') || document.getElementById('ngU') ||
                                      document.getElementById('ngP') || document.getElementById('csel'));
                const gateOnScreen = visById('ngStart') || visById('ngU') || visById('ngP') || visById('csel');

                if (gateMarkup) {
                    if (gateOnScreen || stOutGame) {
                        if (window.AndroidBridge && window.AndroidBridge.onGameKickedDetected) {
                            window.AndroidBridge.onGameKickedDetected(tabIdx);
                        }
                        return "KICKED_DETECTED";
                    }
                } else if (!stInGame) {
                    let isStartOrLoginVisible = bodyNA.includes('bat dau tro choi') ||
                                                bodyNA.includes('dang ky tai khoan') ||
                                                bodyNA.includes('chua co tai khoan') ||
                                                bodyNA.includes('moi tai khoan 1 nhan vat') ||
                                                bodyNA.includes('nho tai khoan') ||
                                                bodyNA.includes('hien mat ma') ||
                                                bodyNA.includes('tao nhan vat');

                    if (!isStartOrLoginVisible) {
                        for (const el of allElements) {
                            const rawTxt = (el.textContent || el.innerText || '').replace(/\s+/g, ' ').trim();
                            if (!rawTxt || rawTxt.length > 35) continue;
                            const tNA = noAccent(rawTxt);
                            if (tNA === 'bat dau tro choi' || tNA === 'dang ky tai khoan' || tNA === 'xac dinh' || tNA === 'tao nhan vat') {
                                if (isVisible(el)) {
                                    isStartOrLoginVisible = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (!isStartOrLoginVisible) {
                        const visibleAuthInputs = Array.from(document.querySelectorAll('input')).filter(inp => {
                            if (inp.readOnly || inp.disabled) return false;
                            const nid = (inp.id || '').toLowerCase();
                            if (nid === 'nau' || nid === 'nap') return false;
                            const t = (inp.type || '').toLowerCase();
                            if (['hidden', 'checkbox', 'radio', 'button', 'submit'].includes(t)) return false;
                            if (!isVisible(inp)) return false;
                            if (t === 'password') return true;
                            const pTxt = inp.parentElement ? (inp.parentElement.textContent || inp.parentElement.innerText || '') : '';
                            const meta = noAccent((inp.placeholder || '') + ' ' + (inp.name || '') + ' ' + (inp.id || '') + ' ' + pTxt);
                            return meta.includes('mat ma') || meta.includes('mat khau') || meta.includes('password') || meta.includes('tai khoan');
                        });
                        if (visibleAuthInputs.length > 0) {
                            isStartOrLoginVisible = true;
                        }
                    }

                    if (isStartOrLoginVisible) {
                        if (window.AndroidBridge && window.AndroidBridge.onGameKickedDetected) {
                            window.AndroidBridge.onGameKickedDetected(tabIdx);
                        }
                        return "KICKED_DETECTED";
                    }
                }

                let charName = '';
                let sect = '';
                let level = '';
                let expPercent = '';
                let daiHongBao = '';
                let hongBao = '';
                let thapRecord = '';
                let bossHoangKim = '';
                let daTauQuest = '';
                let currentModeText = "Idle";

                if (stp) {
                    if (stp.ten) charName = cleanText(String(stp.ten)).slice(0, 25);
                    if (stp.phaiTen) sect = cleanText(String(stp.phaiTen));
                    else if (typeof stp.phai === 'string') sect = cleanText(stp.phai);
                    if (stp.cap !== undefined && stp.cap !== null && stp.cap !== '') level = String(stp.cap | 0);
                    if (typeof stp.xpp === 'number' && isFinite(stp.xpp)) expPercent = String(stp.xpp) + '%';
                    if (stp.dt && stp.dt.length === 2) {
                        daTauQuest = 'Hôm nay ' + (stp.dt[0] | 0) + '/' + (stp.dt[1] | 0);
                    }
                    if (stp.o === 'uythac') {
                        currentModeText = "💤 Đang uỷ thác";
                    }
                }

                if (!level) {
                    const lvEl = document.getElementById('lvS') || document.getElementById('lv');
                    const lvTxt = lvEl ? cleanText(lvEl.textContent || '') : '';
                    if (/^\d+$/.test(lvTxt)) level = lvTxt;
                }
                if (!expPercent) {
                    const xpEl = document.getElementById('xpTxt');
                    const xpTxt = xpEl ? cleanText(xpEl.textContent || '') : '';
                    if (/^\d+(?:[.,]\d+)?%$/.test(xpTxt)) expPercent = xpTxt.replace(',', '.');
                }

                if (!stp) {
                    const NAME_SCAN_LIMIT = 400;
                    function isNameLikeText(txt, txtNA) {
                        if (txt.length < 2 || txt.length > 25) return false;
                        if (/^\d+$/.test(txt)) return false;
                        if (txtNA.includes('luc tay') || txtNA.includes('chien luc') || txtNA.includes('che do') ||
                            txtNA.includes('may chu') || txtNA.includes('vo lam') || txtNA.includes('tai khoan') ||
                            txtNA.includes('mat ma') || txtNA.includes('bat dau') || txtNA.includes('dang ky')) return false;
                        if (txt.includes('Cấp') || txt.includes('HP') || txt.includes('MP') || txt.includes('EXP')) return false;
                        return true;
                    }
                    function scanCharNameIn(nodes) {
                        for (const el of nodes) {
                            if (el.children && el.children.length > 2) continue;
                            const txt = cleanText(el.textContent || '');
                            if (!txt) continue;
                            const txtNA = noAccent(txt);
                            if (!isNameLikeText(txt, txtNA)) continue;
                            const parentText = el.parentElement ? (el.parentElement.textContent || '') : '';
                            const parentNA = noAccent(parentText);
                            if ((parentText.includes('Cấp') || parentText.includes('Lv') || parentText.includes('%')) && !parentNA.includes('luc tay 0')) {
                                if (isVisible(el)) return txt;
                            }
                        }
                        return '';
                    }
                    charName = scanCharNameIn(
                        Array.from(document.querySelectorAll('.player-name, .character-name, .font-bold, .text-yellow-400, .text-amber-400')).slice(0, NAME_SCAN_LIMIT)
                    );
                    if (!charName) {
                        charName = scanCharNameIn(Array.from(document.querySelectorAll('span, div')).slice(0, NAME_SCAN_LIMIT));
                    }

                    const levelMatch = bodyText.match(/(?:Cấp|Lv\.?|Level)\s*:?\s*(\d+)/i) || bodyText.match(/(\d{1,3})\s*(?:cấp|level)/i);
                    if (levelMatch) level = levelMatch[1];
                    const expMatch = bodyText.match(/(\d+(?:\.\d+)?)\s*%/);
                    if (expMatch) expPercent = expMatch[1] + '%';

                    const daiHbMatch = bodyText.match(/Đại\s*hồng\s*bao\s*:?\s*(\d+)/i) || bodyText.match(/🧧\s*Đại\s*:?\s*(\d+)/i);
                    if (daiHbMatch) daiHongBao = daiHbMatch[1];

                    const hbMatch = bodyText.match(/(?:Hồng\s*bao|Bao\s*lì\s*xì)\s*:?\s*(\d+)/i) || bodyText.match(/🧧\s*:?\s*(\d+)/i);
                    if (hbMatch) hongBao = hbMatch[1];

                    const thapMatch = bodyText.match(/(?:Kỷ\s*lục\s*tầng\s*tháp|Tầng\s*tháp|Tháp)\s*:?\s*([^\n,]{1,20})/i);
                    if (thapMatch) thapRecord = cleanText(thapMatch[1]);

                    const bossMatch = bodyText.match(/(?:Boss\s*Hoàng\s*Kim|Boss\s*HK)\s*:?\s*(\d+)/i);
                    if (bossMatch) bossHoangKim = bossMatch[1];

                    const daTauMatch = bodyText.match(/(?:Dã\s*Tẩu|Nhiệm\s*vụ\s*Dã\s*Tẩu)\s*:?\s*([^\n,]{1,25})/i);
                    if (daTauMatch) daTauQuest = cleanText(daTauMatch[1]);
                }

                if (!sect) {
                    const sects = ['Thiếu Lâm', 'Thiên Vương Bang', 'Thiên Vương', 'Đường Môn', 'Ngũ Độc', 'Nga Mi', 'Thuý Yên', 'Thúy Yên', 'Cái Bang', 'Thiên Nhẫn', 'Võ Đang', 'Côn Lôn', 'Đoàn Thị'];
                    for (const s of sects) {
                        if (bodyText.includes(s)) {
                            sect = s;
                            break;
                        }
                    }
                }

                if (currentModeText === "Idle" && (!stp || stp.o === 'game')) {
                    var modeBtn = document.getElementById('mswBtn');
                    if (modeBtn) {
                        var modeTxt = cleanText(modeBtn.textContent || '');
                        modeTxt = modeTxt.replace(/^Chế\s*Độ\s*:\s*/i, '').replace(/[▴▾]/g, '').trim();
                        if (modeTxt) currentModeText = modeTxt;
                    }
                    var stageEl = document.getElementById('stageLbl');
                    var stageTxt = stageEl ? cleanText(stageEl.textContent || '') : '';
                    if (stageTxt.indexOf('⛵') >= 0 || stageTxt.indexOf('Phong Lăng') >= 0) {
                        currentModeText = "⛵ Đang đi thuyền";
                    }
                }

                var statusMsg = '🟢 Đang chạy ổn định';
                var stO = stp ? String(stp.o || '') : '';
                if (stO === 'dangnhap') statusMsg = '🔑 Đang đăng nhập (chưa vào game)';
                else if (stO === 'menu') statusMsg = '📋 Đang ở menu chọn nhân vật';
                else if (stO === 'uythac') statusMsg = '💤 Đang uỷ thác';
                else if (currentModeText !== "Idle") statusMsg = '🟢 ' + currentModeText;
                
                let tasksStr = '';
                try {
                    const vnDay = function () {
                        var v = new Date(Date.now() + 7 * 3600e3 - 1000);
                        return v.getUTCFullYear() + '-' + (v.getUTCMonth() + 1) + '-' + v.getUTCDate();
                    };
                    const vnTradeDay = function () {
                        var v = new Date(Date.now() + 7 * 3600e3);
                        return v.getUTCFullYear() + '-' + (v.getUTCMonth() + 1) + '-' + v.getUTCDate();
                    };
                    const getStat = function(k) {
                        try {
                            var who = stp && stp.ten ? String(stp.ten) : '';
                            var key = 'vlm_auto_' + (who ? who + '_' : '') + k;
                            var o = JSON.parse(localStorage.getItem(key) || 'null');
                            var dayRef = (k === 'theGd') ? vnTradeDay() : vnDay();
                            return o && o.d === dayRef ? (o.n | 0) : 0;
                        } catch(e) { return 0; }
                    };
                    let tasks = [];
                    let notDone = [];
                    let theGd = getStat('theGd'); if (theGd > 0) tasks.push('Thẻ GD');
                    let pbx = getStat('pbx'); if (pbx > 0) tasks.push('Phó bản'); else notDone.push('Phó bản');
                    let k2k = getStat('k2k'); if (k2k > 0) tasks.push('KNB khóa');
                    let lb = getStat('lb'); if (lb > 0) tasks.push('Lệnh Bài');
                    let knb = getStat('knb'); if (knb > 0) tasks.push('KNB');
                    let qht = getStat('qht'); if (qht > 0) tasks.push('Quế Hoa Tửu(' + qht + ')');
                    
                    if (tasks.length === 0) tasks.push('Chưa có');
                    tasksStr = tasks.join(', ');
                    if (notDone.length > 0) tasksStr += ' | Chưa xong: ' + notDone.join(', ');
                } catch(e) {}

                if (window.AndroidBridge && window.AndroidBridge.onGameStateScanned) {
                    window.AndroidBridge.onGameStateScanned(tabIdx, JSON.stringify({
                        isOnline: true,
                        characterName: charName,
                        sect: sect,
                        level: level,
                        expPercent: expPercent,
                        daiHongBao: daiHongBao,
                        hongBao: hongBao,
                        thapRecord: thapRecord,
                        bossHoangKim: bossHoangKim,
                        daTauQuest: daTauQuest,
                        tasksCompleted: tasksStr,
                        lastStatus: statusMsg
                    }));
                }
                return "SCANNED_SUCCESS";
            } catch(e) {
                return "ERROR: " + e.message;
            }
        })();
        """.trimIndent()
    }

    fun buildDomGarbageCollectionScript(): String {
        return """
        (function() {
            try {
                const ae = document.activeElement;
                if (ae) {
                    const t = (ae.tagName || '').toUpperCase();
                    if (t === 'INPUT' || t === 'TEXTAREA' || t === 'SELECT' || ae.isContentEditable) return;
                }
                const chatSelectors = ['#chatLines', '#u20lines', '#logBox', '#u20log'];
                for (const selector of chatSelectors) {
                    const containers = document.querySelectorAll(selector);
                    containers.forEach(container => {
                        const items = Array.from(container.children);
                        if (items.length > 12) {
                            const toRemove = items.length - 12;
                            for (let i = 0; i < toRemove; i++) {
                                if (items[i] && items[i].remove) {
                                    items[i].remove();
                                }
                            }
                        }
                    });
                }
                const maxFloating = 4;
                const floatingSelectors = ['.floating-text', '.damage-text', '.damage-number', '.hit-fx', '.particle'];
                for (const sel of floatingSelectors) {
                    const nodes = document.querySelectorAll(sel);
                    if (nodes.length > maxFloating) {
                        for (let i = 0; i < nodes.length - maxFloating; i++) {
                            try { nodes[i].remove(); } catch(e) {}
                        }
                    }
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }

    fun buildRenderOptimizationScript(hideMap: Boolean): String {
        val hMap = if (hideMap) "true" else "false"
        return """
        (function(hideMap) {
            try {
                window.__vlmHideMap = !!hideMap;
                window.__vlmThermalEco = true;

                try {
                    const root = document.documentElement;
                    if (root && root.classList) {
                        root.classList.toggle('__vlm-hide-map', !!hideMap);
                    }
                } catch(eCls) {}

                const styleId = '__vlm_render_opt_css';
                if (!document.getElementById(styleId) && (document.head || document.documentElement)) {
                    const st = document.createElement('style');
                    st.id = styleId;
                    st.textContent = `
                        html.__vlm-hide-map #mapCanvas,
                        html.__vlm-hide-map #bgCanvas,
                        html.__vlm-hide-map #terrainCanvas,
                        html.__vlm-hide-map #worldMap,
                        html.__vlm-hide-map #minimap,
                        html.__vlm-hide-map .minimap,
                        html.__vlm-hide-map .map-bg,
                        html.__vlm-hide-map .map-layer,
                        html.__vlm-hide-map .tile-layer,
                        html.__vlm-hide-map [data-layer="map"],
                        html.__vlm-hide-map img[src*="/map" i],
                        html.__vlm-hide-map img[src*="bando" i],
                        html.__vlm-hide-map img[src*="terrain" i],
                        html.__vlm-hide-map img[src*="tile" i] {
                            display: none !important;
                            visibility: hidden !important;
                            opacity: 0 !important;
                        }
                        html.__vlm-hide-map body,
                        html.__vlm-hide-map #game,
                        html.__vlm-hide-map #stage,
                        html.__vlm-hide-map #arena,
                        html.__vlm-hide-map .game-stage,
                        html.__vlm-hide-map canvas {
                            background-color: #000 !important;
                            background-image: none !important;
                        }
                    `;
                    (document.head || document.documentElement).appendChild(st);
                }

                if (window.__vlmCanvasOptInstalled) return;
                window.__vlmCanvasOptInstalled = true;

                const classCache = (typeof WeakMap === 'function') ? new WeakMap() : null;
                const mapPatterns = (typeof WeakSet === 'function') ? new WeakSet() : null;
                const mapRe = /\/map|bando|terrain|ground|tile|backdrop|scenebg|\/bg\/|_bg|background|worldmap|minimap/i;

                function isMapSource(img) {
                    if (!img) return false;
                    if (classCache && classCache.has(img)) return classCache.get(img);
                    let isMap = false;
                    try {
                        const ds = img.dataset || {};
                        const rawSrc = typeof img.currentSrc === 'string' && img.currentSrc
                            ? img.currentSrc
                            : (typeof img.src === 'string' ? img.src : '');
                        const shortSrc = rawSrc.indexOf('data:') === 0 ? '' : rawSrc.slice(0, 180);
                        const meta = (
                            shortSrc + ' ' +
                            (img.id || '') + ' ' +
                            (typeof img.className === 'string' ? img.className : '') + ' ' +
                            (img.alt || '') + ' ' +
                            (ds.type || '') + ' ' + (ds.kind || '') + ' ' + (ds.layer || '') + ' ' + (ds.role || '')
                        );
                        if (meta.trim()) {
                            isMap = mapRe.test(meta);
                        }
                    } catch(e) {}
                    if (classCache && typeof img === 'object') {
                        try { classCache.set(img, isMap); } catch(e2) {}
                    }
                    return isMap;
                }

                function shouldSkipAllRender() {
                    return !!(window.__isSleepMode || window.__isAppInBackground);
                }

                function ensureClearedForFrame(ctx, origClear) {
                    const cv = ctx.canvas;
                    if (!cv) return;
                    const seq = window.__vlmFrameSeq || 0;
                    if (seq && cv.__vlmClearedSeq !== seq) {
                        cv.__vlmClearedSeq = seq;
                        try { origClear.call(ctx, 0, 0, cv.width, cv.height); } catch(e) {}
                    }
                }

                if (typeof CanvasRenderingContext2D !== 'undefined' && CanvasRenderingContext2D.prototype) {
                    const proto = CanvasRenderingContext2D.prototype;
                    const origClearRect = proto.clearRect;
                    const origDrawImage = proto.drawImage;
                    const origFillRect = proto.fillRect;
                    const origStrokeRect = proto.strokeRect;
                    const origFillText = proto.fillText;
                    const origStrokeText = proto.strokeText;
                    const origFill = proto.fill;
                    const origStroke = proto.stroke;
                    const origCreatePattern = proto.createPattern;

                    proto.clearRect = function(x, y, w, h) {
                        if (shouldSkipAllRender()) return;
                        try {
                            const cv = this.canvas;
                            if (cv && w >= cv.width * 0.5 && h >= cv.height * 0.5) {
                                cv.__vlmClearedSeq = window.__vlmFrameSeq || 0;
                            }
                        } catch(e) {}
                        return origClearRect.apply(this, arguments);
                    };

                    if (origCreatePattern) {
                        proto.createPattern = function(image, repetition) {
                            const pat = origCreatePattern.apply(this, arguments);
                            try {
                                if (pat && mapPatterns && isMapSource(image)) {
                                    mapPatterns.add(pat);
                                }
                            } catch(e) {}
                            return pat;
                        };
                    }

                    proto.drawImage = function(img) {
                        if (shouldSkipAllRender()) return;
                        if (this.shadowBlur > 0) this.shadowBlur = 0;
                        if (window.__vlmHideMap) {
                            ensureClearedForFrame(this, origClearRect);
                            if (isMapSource(img)) return;
                            try {
                                const cv = this.canvas;
                                let dw = 0, dh = 0;
                                if (arguments.length >= 9) {
                                    dw = Math.abs(arguments[7] || 0);
                                    dh = Math.abs(arguments[8] || 0);
                                } else if (arguments.length >= 5) {
                                    dw = Math.abs(arguments[3] || 0);
                                    dh = Math.abs(arguments[4] || 0);
                                } else if (img) {
                                    dw = img.naturalWidth || img.width || 0;
                                    dh = img.naturalHeight || img.height || 0;
                                }
                                if (cv && cv.width > 0 && cv.height > 0 &&
                                    dw >= cv.width * 0.45 && dh >= cv.height * 0.45) {
                                    return;
                                }
                            } catch(eDim) {}
                        }
                        return origDrawImage.apply(this, arguments);
                    };

                    proto.fillRect = function(x, y, w, h) {
                        if (shouldSkipAllRender()) return;
                        if (window.__vlmHideMap) {
                            if (mapPatterns && this.fillStyle && mapPatterns.has(this.fillStyle)) return;
                            try {
                                const cv = this.canvas;
                                if (cv && w >= cv.width * 0.85 && h >= cv.height * 0.85) {
                                    const fs = String(this.fillStyle || '').toLowerCase();
                                    if (fs !== '#000000' && fs !== '#000' && fs !== 'black' && fs !== 'rgb(0, 0, 0)') {
                                        ensureClearedForFrame(this, origClearRect);
                                        return;
                                    }
                                }
                            } catch(eFr) {}
                        }
                        return origFillRect.apply(this, arguments);
                    };

                    proto.strokeRect = function() {
                        if (shouldSkipAllRender()) return;
                        return origStrokeRect.apply(this, arguments);
                    };

                    proto.fillText = function(text) {
                        if (shouldSkipAllRender()) return;
                        if (this.shadowBlur > 0) this.shadowBlur = 0;
                        return origFillText.apply(this, arguments);
                    };

                    proto.strokeText = function(text) {
                        if (shouldSkipAllRender()) return;
                        if (this.shadowBlur > 0) this.shadowBlur = 0;
                        return origStrokeText.apply(this, arguments);
                    };

                    proto.fill = function() {
                        if (shouldSkipAllRender()) return;
                        if (window.__vlmHideMap && mapPatterns && this.fillStyle && mapPatterns.has(this.fillStyle)) return;
                        return origFill.apply(this, arguments);
                    };

                    proto.stroke = function() {
                        if (shouldSkipAllRender()) return;
                        return origStroke.apply(this, arguments);
                    };
                }

                if (typeof WebGLRenderingContext !== 'undefined' && WebGLRenderingContext.prototype) {
                    const glp = WebGLRenderingContext.prototype;
                    const origDrawArrays = glp.drawArrays;
                    const origDrawElements = glp.drawElements;
                    if (origDrawArrays) {
                        glp.drawArrays = function() {
                            if (shouldSkipAllRender()) return;
                            return origDrawArrays.apply(this, arguments);
                        };
                    }
                    if (origDrawElements) {
                        glp.drawElements = function() {
                            if (shouldSkipAllRender()) return;
                            return origDrawElements.apply(this, arguments);
                        };
                    }
                }
            } catch(e) {}
        })($hMap);
        """.trimIndent()
    }

    fun buildFpsLimiterScript(fps: Int, isEmulator: Boolean = false): String {
        val intervalMs = if (fps <= 30) 33 else 16
        return """
        (function() {
            window.__targetFrameIntervalMs = $intervalMs;
            window.__isEmulatorEnv = $isEmulator;
            if (window.__fpsThrottlerInjected) return;
            window.__fpsThrottlerInjected = true;

            try {
                Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                document.hasFocus = function() { return true; };

                const blockBgEvents = function(e) {
                    try {
                        e.stopImmediatePropagation();
                        e.stopPropagation();
                    } catch(err) {}
                };
                window.addEventListener('visibilitychange', blockBgEvents, true);
                document.addEventListener('visibilitychange', blockBgEvents, true);
                document.addEventListener('webkitvisibilitychange', blockBgEvents, true);
                window.addEventListener('blur', function(e) {
                    if (e.target === window || e.target === document) blockBgEvents(e);
                }, true);
                window.addEventListener('pagehide', blockBgEvents, true);
            } catch(e) {}

            let lastFrameTime = performance.now();
            let virtualTime = lastFrameTime;
            const originalRAF = window.requestAnimationFrame ? window.requestAnimationFrame.bind(window) : null;
            const originalCAF = window.cancelAnimationFrame ? window.cancelAnimationFrame.bind(window) : null;

            const pendingRafHandles = new Map();
            let nextRafHandleId = 1;

            window.requestAnimationFrame = function(callback) {
                const targetInterval = window.__targetFrameIntervalMs || 33;
                const bgInterval = window.__isSleepMode ? 340 : 240;
                const handleId = nextRafHandleId++;
                const handle = { timer: null, innerTimer: null, rafId: null, done: false };
                pendingRafHandles.set(handleId, handle);

                function releaseHandle() {
                    if (handle.timer !== null) { clearTimeout(handle.timer); handle.timer = null; }
                    if (handle.innerTimer !== null) { clearTimeout(handle.innerTimer); handle.innerTimer = null; }
                    pendingRafHandles.delete(handleId);
                }

                function invokeSmoothly(rawNow) {
                    if (handle.done) return;
                    handle.done = true;
                    releaseHandle();

                    const now = (typeof rawNow === 'number' && rawNow > 0) ? rawNow : performance.now();
                    const elapsed = now - lastFrameTime;
                    lastFrameTime = now;

                    const safeDelta = (elapsed > 0 && elapsed <= 50) ? elapsed : Math.min(Math.max(elapsed, 16), 50);
                    virtualTime += safeDelta;
                    window.__vlmFrameSeq = ((window.__vlmFrameSeq || 0) + 1) | 0;

                    try {
                        callback(virtualTime);
                    } catch(err) {}
                }

                if (window.__isAppInBackground || window.__isSleepMode || !originalRAF) {
                    handle.timer = setTimeout(function() {
                        invokeSmoothly(performance.now());
                    }, bgInterval);
                    return handleId;
                }

                const nowCheck = performance.now();
                const waitMs = Math.max(0, targetInterval - (nowCheck - lastFrameTime));

                handle.timer = setTimeout(function() {
                    invokeSmoothly(performance.now());
                }, Math.max(waitMs + 45, 75));

                const scheduleVSync = function() {
                    if (handle.done || !originalRAF) return;
                    handle.rafId = originalRAF(function(ts) {
                        handle.rafId = null;
                        invokeSmoothly(ts);
                    });
                };

                if (waitMs <= 4) {
                    scheduleVSync();
                } else {
                    handle.innerTimer = setTimeout(function() {
                        handle.innerTimer = null;
                        if (handle.done) return;
                        scheduleVSync();
                    }, waitMs);
                }

                return handleId;
            };

            window.cancelAnimationFrame = function(handleId) {
                const handle = pendingRafHandles.get(handleId);
                if (!handle) {
                    if (originalCAF) { try { originalCAF(handleId); } catch(e) {} }
                    return;
                }
                handle.done = true;
                if (handle.timer !== null) { clearTimeout(handle.timer); handle.timer = null; }
                if (handle.innerTimer !== null) { clearTimeout(handle.innerTimer); handle.innerTimer = null; }
                if (handle.rafId !== null && originalCAF) {
                    try { originalCAF(handle.rafId); } catch(e) {}
                    handle.rafId = null;
                }
                pendingRafHandles.delete(handleId);
            };
        })();
        """.trimIndent()
    }

    fun buildSyncAutoModeScript(modeText: String): String {
        val safeModeJson = quote(modeText)
        return """
        (function(rawMode) {
            try {
                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function realClick(el) {
                    if (!el) return;
                    try {
                        const rect = el.getBoundingClientRect();
                        const cx = rect.left + rect.width / 2;
                        const cy = rect.top + rect.height / 2;
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'].forEach(type => {
                            try {
                                el.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    if (typeof el.click === 'function') {
                        try { el.click(); } catch(e) {}
                    }
                }

                const reqNoAccent = noAccent(rawMode);
                let titleCaseMode = rawMode;
                let lowerMode = norm(rawMode);
                let valueAliases = [];

                if (reqNoAccent.includes('nhiem vu') || reqNoAccent.includes('da tau') || reqNoAccent.includes('quest')) {
                    titleCaseMode = 'Nhiệm Vụ';
                    lowerMode = 'nhiệm vụ';
                    valueAliases = ['nhiem vu', 'nhiemvu', 'nhiem_vu', 'quest', 'task', 'datau', 'da tau'];
                } else if (reqNoAccent.includes('pho ban') || reqNoAccent.includes('dungeon')) {
                    titleCaseMode = 'Phó Bản';
                    lowerMode = 'phó bản';
                    valueAliases = ['pho ban', 'phoban', 'pho_ban', 'dungeon', 'instance'];
                } else if (reqNoAccent.includes('luyen cong') || reqNoAccent.includes('train') || reqNoAccent.includes('grind')) {
                    titleCaseMode = 'Luyện Công';
                    lowerMode = 'luyện công';
                    valueAliases = ['luyen cong', 'luyencong', 'luyen_cong', 'train', 'training', 'grind', 'farm'];
                }

                const targetNoAccent = noAccent(titleCaseMode);

                var modeId = '';
                if (targetNoAccent === 'nhiem vu') modeId = 'quest';
                else if (targetNoAccent === 'pho ban') modeId = 'dg';
                else if (targetNoAccent === 'luyen cong') modeId = 'farm';
                else if (targetNoAccent === 'leo thap') modeId = 'tower';

                function clickModeMenuItem() {
                    if (!modeId) return false;
                    var item = document.querySelector('#mswMenu [data-msw="' + modeId + '"]');
                    if (!item) return false;
                    try { item.click(); return true; } catch (eMsw) { return false; }
                }

                function syncModeViaChannel() {
                    if (!modeId) return false;
                    var msw = document.getElementById('modeSw');
                    if (!msw || msw.classList.contains('hidden')) return false;

                    var mswBtn = document.getElementById('mswBtn');
                    if (mswBtn && noAccent(mswBtn.textContent || '').indexOf(targetNoAccent) >= 0) {
                        return 'ALREADY_IN_MODE:' + titleCaseMode;
                    }

                    var host = document.body || document.documentElement;
                    if (!host) return false;

                    var btn = document.createElement('button');
                    btn.id = 'vlmAuto';
                    btn.type = 'button';
                    btn.style.display = 'none';
                    btn.dataset.act = 'cheDo';
                    btn.dataset.arg = JSON.stringify({ m: modeId });
                    host.appendChild(btn);
                    try { btn.click(); } catch (eC0) {
                        try { btn.remove(); } catch (eC1) {}
                        return false;
                    }

                    var deadline = Date.now() + 6000;
                    var poll = function () {
                        var finished = false;
                        try { finished = !!btn.dataset.xong; } catch (eC2) {}
                        if (finished) {
                            var kq = {};
                            try { kq = JSON.parse(btn.dataset.kq || '{}') || {}; } catch (eC3) {}
                            try { btn.remove(); } catch (eC4) {}
                            if (kq.ok !== true) {
                                if (!clickModeMenuItem()) {
                                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                                        window.AndroidBridge.onGameLog(0, '⚠ Không đổi được chế độ ' + titleCaseMode + ': ' + String(kq.why || 'không rõ') + ' — thử lại');
                                    }
                                }
                            }
                            return;
                        }
                        if (Date.now() >= deadline) {
                            try { btn.remove(); } catch (eC5) {}
                            clickModeMenuItem();
                            return;
                        }
                        setTimeout(poll, 120);
                    };
                    setTimeout(poll, 120);
                    return 'OPENED_TRIGGER_AND_SELECTING:' + titleCaseMode;
                }

                var channelResult = syncModeViaChannel();
                if (channelResult) return channelResult;

                function isMatchingOption(textStr, valStr) {
                    const tRaw = (textStr || '').trim();
                    const vRaw = (valStr || '').trim();
                    if (tRaw.includes(titleCaseMode) || vRaw.includes(titleCaseMode)) return true;
                    const tNorm = norm(tRaw);
                    const vNorm = norm(vRaw);
                    if (tNorm.includes(lowerMode) || vNorm.includes(lowerMode)) return true;
                    const tNA = noAccent(tRaw);
                    const vNA = noAccent(vRaw);
                    if (tNA.includes(targetNoAccent) || vNA.includes(targetNoAccent)) return true;
                    return valueAliases.some(alias => tNA.includes(alias) || vNA === alias || vNA.includes(alias));
                }

                const selects = Array.from(document.querySelectorAll('select'));
                let targetSelect = null;

                for (const s of selects) {
                    const opts = Array.from(s.options || []);
                    const isModeSelect = opts.some(o => {
                        const na = noAccent(o.text || o.textContent || '');
                        const va = noAccent(o.value || '');
                        return na.includes('pho ban') || na.includes('luyen cong') || na.includes('nhiem vu') ||
                               va.includes('phoban') || va.includes('luyencong') || va.includes('nhiemvu') ||
                               va.includes('dungeon') || va.includes('train') || va.includes('quest');
                    });
                    if (isModeSelect) {
                        targetSelect = s;
                        break;
                    }
                }

                if (!targetSelect) {
                    for (const s of selects) {
                        const parentText = s.parentElement ? s.parentElement.innerText : '';
                        const containerText = s.closest('label, div') ? s.closest('label, div').innerText : '';
                        if (noAccent(parentText).includes('che do') || noAccent(containerText).includes('che do')) {
                            targetSelect = s;
                            break;
                        }
                    }
                }

                if (targetSelect) {
                    const options = Array.from(targetSelect.options);
                    for (let i = 0; i < options.length; i++) {
                        const opt = options[i];
                        if (isMatchingOption(opt.text || opt.textContent, opt.value)) {
                            targetSelect.selectedIndex = i;
                            opt.selected = true;

                            if (targetSelect._valueTracker) {
                                try { targetSelect._valueTracker.setValue(''); } catch(e) {}
                            }
                            try {
                                const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value')?.set;
                                if (nativeSetter) {
                                    nativeSetter.call(targetSelect, opt.value);
                                } else {
                                    targetSelect.value = opt.value;
                                }
                            } catch(e) {
                                targetSelect.value = opt.value;
                            }
                            targetSelect.value = opt.value;
                            targetSelect.dispatchEvent(new Event('input', { bubbles: true }));
                            targetSelect.dispatchEvent(new Event('change', { bubbles: true }));
                            return "SELECTED_OPTION:" + (opt.text || opt.value);
                        }
                    }
                }

                function isBottomMenuBarElement(el) {
                    let cur = el;
                    for (let depth = 0; depth < 4 && cur; depth++) {
                        const t = noAccent(cur.innerText || '');
                        if ((t.includes('hanh trang') && t.includes('he thong')) ||
                            (t.includes('vo cong') && t.includes('lo ren')) ||
                            (t.includes('nhan vat') && t.includes('to doi'))) {
                            return true;
                        }
                        cur = cur.parentElement;
                    }
                    return false;
                }

                const candidates = Array.from(document.querySelectorAll('button, div[role="button"], li, option, span, a, div'));

                for (const b of candidates) {
                    if (b.children && b.children.length > 2) continue;
                    const txt = (b.innerText || b.textContent || '').trim();
                    if (!txt || txt.length > 35) continue;
                    if (isBottomMenuBarElement(b)) continue;
                    const txtNA = noAccent(txt);
                    if (txtNA.includes('che do')) continue;

                    if (txt === titleCaseMode || txt.includes(titleCaseMode) || txtNA === targetNoAccent || txtNA.includes(targetNoAccent)) {
                        realClick(b);
                        return "CLICKED_DIRECT:" + txt;
                    }
                }

                let modeTrigger = null;
                for (const el of candidates) {
                    const txt = (el.innerText || el.textContent || '').trim();
                    const txtNA = noAccent(txt);
                    if (txtNA.startsWith('che do:') || txtNA.startsWith('che do :')) {
                        if (txt.length <= 45) {
                            modeTrigger = el;
                            break;
                        }
                    }
                }

                if (modeTrigger) {
                    const currentTriggerNA = noAccent(modeTrigger.innerText || modeTrigger.textContent || '');
                    if (currentTriggerNA.includes(targetNoAccent)) {
                        return "ALREADY_IN_MODE:" + titleCaseMode;
                    }
                    realClick(modeTrigger);
                    setTimeout(() => {
                        try {
                            const popupItems = Array.from(document.querySelectorAll('button, div[role="button"], li, span, a, div'));
                            for (const item of popupItems) {
                                if (item === modeTrigger) continue;
                                if (item.children && item.children.length > 2) continue;
                                const t = (item.innerText || item.textContent || '').trim();
                                if (!t || t.length > 35) continue;
                                if (isBottomMenuBarElement(item)) continue;
                                const tNA = noAccent(t);
                                if (tNA.includes('che do')) continue;
                                if (t === titleCaseMode || t.includes(titleCaseMode) || tNA.includes(targetNoAccent)) {
                                    realClick(item);
                                    break;
                                }
                            }
                        } catch(e) {}
                    }, 80);
                    return "OPENED_TRIGGER_AND_SELECTING:" + titleCaseMode;
                }

                return "NOT_FOUND";
            } catch(e) {
                return "ERROR:" + e.message;
            }
        })($safeModeJson);
        """.trimIndent()
    }

    fun buildSyncBlackScreenScript(enabled: Boolean): String {
        val targetState = if (enabled) "true" else "false"
        return """
        (function(targetEnabled) {
            try {
                function norm(str) {
                    return (str || '').toString().toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
                }

                function noAccent(str) {
                    return norm(str)
                        .normalize('NFD')
                        .replace(/[\u0300-\u036f]/g, '')
                        .replace(/đ/g, 'd');
                }

                function isIgnoredLog(el) {
                    if (!el || !el.closest) return false;
                    return !!el.closest('#chatLines, #u20lines, #logBox, #u20log, #chatBox');
                }

                function isVisible(el) {
                    if (!el || !el.isConnected) return false;
                    try {
                        if (el.getClientRects && el.getClientRects().length === 0) return false;
                        let cur = el;
                        while (cur && cur !== document.body && cur !== document.documentElement) {
                            if (cur.hidden) return false;
                            if (cur.classList && cur.classList.contains('hidden')) return false;
                            const st = window.getComputedStyle(cur);
                            if (st && (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0)) {
                                return false;
                            }
                            cur = cur.parentElement;
                        }
                    } catch(e) {}
                    return true;
                }

                function realClick(el) {
                    if (!el) return;
                    const target = el.closest('button, [role="button"], [role="tab"], [role="switch"], a, label') || el;
                    try {
                        target.disabled = false;
                        target.removeAttribute('disabled');
                    } catch(e) {}
                    try {
                        const rect = target.getBoundingClientRect();
                        const cx = rect.left + Math.max(2, rect.width / 2);
                        const cy = rect.top + Math.max(2, rect.height / 2);
                        ['pointerdown', 'mousedown', 'pointerup', 'mouseup'].forEach(type => {
                            try {
                                target.dispatchEvent(new MouseEvent(type, {
                                    view: window, bubbles: true, cancelable: true, clientX: cx, clientY: cy, buttons: 1
                                }));
                            } catch(e) {}
                        });
                    } catch(e) {}
                    // Chỉ gọi target.click() một lần duy nhất (không dispatch thêm MouseEvent 'click' gây bật rồi tắt ngay)
                    if (typeof target.click === 'function') {
                        try { target.click(); } catch(e) {}
                    } else {
                        try {
                            target.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true }));
                        } catch(e) {}
                    }
                }

                function getModalRoot() {
                    const md = document.getElementById('modal');
                    if (md && !md.classList.contains('hidden')) {
                        return document.getElementById('mBody') || md;
                    }
                    return null;
                }

                function findBlackScreenCheckbox() {
                    const root = getModalRoot() || document;
                    const checkboxes = Array.from(root.querySelectorAll('input[type="checkbox"], [role="switch"]'));
                    for (const cb of checkboxes) {
                        if (isIgnoredLog(cb)) continue;
                        const labelEl = cb.closest('label');
                        const rowEl = cb.closest('.qrow, .opt, .row, li, tr, div');
                        const labelText = noAccent(labelEl ? (labelEl.textContent || labelEl.innerText) : '');
                        const parentText = noAccent(cb.parentElement ? (cb.parentElement.textContent || cb.parentElement.innerText) : '');
                        const nextText = noAccent(cb.nextElementSibling ? (cb.nextElementSibling.textContent || cb.nextElementSibling.innerText) : '');
                        const rowText = noAccent(rowEl && (rowEl.textContent || '').length < 180 ? (rowEl.textContent || '') : '');
                        const combined = labelText + ' ' + parentText + ' ' + nextText + ' ' + rowText;
                        if (combined.includes('man hinh den') || combined.includes('tat man hinh') || combined.includes('black screen')) {
                            return cb;
                        }
                    }
                    return null;
                }

                function findBlackScreenButton() {
                    const root = getModalRoot() || document;
                    const btns = Array.from(root.querySelectorAll('button, [role="button"], a'));
                    for (const b of btns) {
                        if (isIgnoredLog(b) || !isVisible(b)) continue;
                        const t = noAccent(b.textContent || b.innerText || '');
                        if (t.includes('man hinh den') || t.includes('tat man hinh')) {
                            return { el: b, type: 'direct' };
                        }
                        const row = b.closest('.qrow, .opt, .row, li, tr, div');
                        const rTxt = row ? noAccent(row.textContent || row.innerText || '') : '';
                        if ((rTxt.includes('man hinh den') || rTxt.includes('tat man hinh')) && rTxt.length < 180) {
                            if (t === 'bat' || t === 'tat' || t === 'mo' || t === 'dong' || t.includes('dang bat') || t.includes('dang tat')) {
                                return { el: b, type: 'toggle' };
                            }
                        }
                    }
                    return null;
                }

                function setCheckboxState(cb, desired) {
                    if (!cb) return false;
                    const isRoleSwitch = (cb.getAttribute && cb.getAttribute('role') === 'switch');
                    const getChecked = () => isRoleSwitch ? (cb.getAttribute('aria-checked') === 'true') : Boolean(cb.checked);
                    if (getChecked() === Boolean(desired)) {
                        return true;
                    }
                    try { cb.focus(); } catch(e) {}
                    try { cb.click(); } catch(e) {}
                    if (getChecked() === Boolean(desired)) {
                        return true;
                    }
                    const parentLabel = cb.closest('label');
                    if (parentLabel) {
                        try { parentLabel.click(); } catch(e) {}
                        if (getChecked() === Boolean(desired)) {
                            return true;
                        }
                    }
                    if (!isRoleSwitch) {
                        try {
                            const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'checked')?.set;
                            if (setter) setter.call(cb, desired);
                            else cb.checked = desired;
                        } catch(e) {
                            cb.checked = desired;
                        }
                        if (cb._valueTracker) {
                            try { cb._valueTracker.setValue(String(!desired)); } catch(e) {}
                        }
                        try { cb.dispatchEvent(new Event('input', { bubbles: true })); } catch(e) {}
                        try { cb.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                    }
                    return getChecked() === Boolean(desired);
                }

                function applyBlackScreenControl() {
                    const cb = findBlackScreenCheckbox();
                    if (cb) {
                        setCheckboxState(cb, targetEnabled);
                        return true;
                    }
                    const btnInfo = findBlackScreenButton();
                    if (btnInfo && btnInfo.el) {
                        const t = noAccent(btnInfo.el.textContent || btnInfo.el.innerText || '');
                        if (btnInfo.type === 'toggle') {
                            const currentlyOn = (t === 'tat' || t === 'dong' || t.includes('dang bat') || t === 'on');
                            if (currentlyOn !== Boolean(targetEnabled)) {
                                realClick(btnInfo.el);
                            }
                            return true;
                        } else {
                            const ariaPressed = btnInfo.el.getAttribute && btnInfo.el.getAttribute('aria-pressed');
                            const hasOnClass = btnInfo.el.classList && (
                                btnInfo.el.classList.contains('on') ||
                                btnInfo.el.classList.contains('active') ||
                                btnInfo.el.classList.contains('checked')
                            );
                            const saysOn = t.includes(': bat') || t.includes('dang bat') || t.includes('(bat)');
                            const saysOff = t.includes(': tat') || t.includes('dang tat') || t.includes('(tat)');
                            if (ariaPressed === 'true' || ariaPressed === 'false' || saysOn || saysOff) {
                                const currentlyOn = (ariaPressed === 'true') || saysOn || (!saysOff && hasOnClass);
                                if (currentlyOn !== Boolean(targetEnabled)) {
                                    realClick(btnInfo.el);
                                }
                            } else if (hasOnClass) {
                                if (!targetEnabled) {
                                    realClick(btnInfo.el);
                                }
                            } else if (targetEnabled) {
                                realClick(btnInfo.el);
                            }
                            return true;
                        }
                    }
                    return false;
                }

                function tryWakeBlackScreenOverlayIfNeeded() {
                    if (targetEnabled) return false;
                    const els = Array.from(document.querySelectorAll('div, canvas, span, p, button'));
                    for (const el of els) {
                        if (isIgnoredLog(el)) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (((t.includes('man hinh den') && t.includes('game van chay')) ||
                             t.includes('bam vao man hinh de bat') ||
                             t.includes('cham de bat man hinh')) && t.length < 240) {
                            realClick(el);
                            return true;
                        }
                    }
                    return false;
                }

                function findHeThongButton() {
                    const directIds = ['sysBtn', 'heThongBtn', 'btnSys', 'u20sys', 'setBtn', 'optBtn'];
                    for (const id of directIds) {
                        const byId = document.getElementById(id);
                        if (byId && isVisible(byId)) return byId;
                    }
                    const attrMatch = document.querySelector('[title*="Hệ thống" i], [aria-label*="Hệ thống" i], [data-open="sys"], [data-tab="sys"]');
                    if (attrMatch && isVisible(attrMatch)) return attrMatch;
                    const md = document.getElementById('modal');
                    const primaryEls = Array.from(document.querySelectorAll('button, [role="button"], a'));
                    for (const el of primaryEls) {
                        if (md && md.contains(el)) continue;
                        if (isIgnoredLog(el) || !isVisible(el)) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'he thong' || (t.includes('he thong') && t.length <= 22)) {
                            return el;
                        }
                    }
                    const fallbackEls = Array.from(document.querySelectorAll('div, span'));
                    for (const el of fallbackEls) {
                        if (md && md.contains(el)) continue;
                        if (isIgnoredLog(el) || !isVisible(el)) continue;
                        if (el.children && el.children.length > 3) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'he thong' || (t.includes('he thong') && t.length <= 18)) {
                            return el;
                        }
                    }
                    return null;
                }

                function findTuyChonTab() {
                    const root = getModalRoot() || document;
                    const primaryEls = Array.from(root.querySelectorAll('button, [role="tab"], [role="button"], li, a'));
                    for (const el of primaryEls) {
                        if (isIgnoredLog(el) || !isVisible(el)) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'tuy chon' || (t.includes('tuy chon') && !t.includes('man hinh') && t.length <= 22)) {
                            return el;
                        }
                    }
                    const fallbackEls = Array.from(root.querySelectorAll('div, span'));
                    for (const el of fallbackEls) {
                        if (isIgnoredLog(el) || !isVisible(el)) continue;
                        if (el.children && el.children.length > 3) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'tuy chon' || (t.includes('tuy chon') && !t.includes('man hinh') && t.length <= 20)) {
                            return el;
                        }
                    }
                    return null;
                }

                function findHienThiTab() {
                    const tuyChon = findTuyChonTab();
                    if (tuyChon) return tuyChon;
                    const root = getModalRoot() || document;
                    const els = Array.from(root.querySelectorAll('button, [role="tab"], [role="button"], li, a, div, span'));
                    for (const el of els) {
                        if (isIgnoredLog(el) || !isVisible(el)) continue;
                        if (el.children && el.children.length > 3) continue;
                        const t = noAccent(el.innerText || el.textContent || '');
                        if (t === 'hien thi' || (t.includes('hien thi') && !t.includes('man hinh') && !t.includes('hieu ung') && t.length <= 20)) {
                            return el;
                        }
                    }
                    return null;
                }

                function ensureBlackScreenStealthCss() {
                    try {
                        const id = '__vlm_stealth_css';
                        if (document.getElementById(id)) return;
                        const st = document.createElement('style');
                        st.id = id;
                        st.textContent =
                            'html.__vlm-stealth #modal, html.__vlm-stealth #modal *, html.__vlm-stealth .modal, html.__vlm-stealth .modal *, html.__vlm-stealth [role="dialog"], html.__vlm-stealth [role="dialog"] * { pointer-events: none !important; transition: none !important; animation: none !important; } ' +
                            'html.__vlm-stealth #modal, html.__vlm-stealth .modal, html.__vlm-stealth [role="dialog"] { opacity: 0.001 !important; z-index: -9999 !important; }';
                        (document.head || document.documentElement).appendChild(st);
                    } catch(e) {}
                }

                function blackScreenStealth(enter) {
                    try {
                        if (!window.__vlmStealth) {
                            window.__vlmStealth = {
                                count: 0,
                                enter: function () {
                                    ensureBlackScreenStealthCss();
                                    this.count = (this.count | 0) + 1;
                                    try { document.documentElement.classList.add('__vlm-stealth'); } catch(e) {}
                                },
                                leave: function () {
                                    this.count = Math.max(0, (this.count | 0) - 1);
                                    if (this.count === 0) {
                                        try { document.documentElement.classList.remove('__vlm-stealth'); } catch(e) {}
                                    }
                                }
                            };
                        }
                        if (enter) window.__vlmStealth.enter();
                        else window.__vlmStealth.leave();
                    } catch(e) {}
                }

                function closeModalIfOpenedByUs(openedByUs) {
                    if (!openedByUs) return;
                    setTimeout(() => {
                        try {
                            const md = document.getElementById('modal');
                            if (md && !md.classList.contains('hidden')) {
                                const closeBtn = document.getElementById('mClose');
                                if (closeBtn) closeBtn.click();
                                setTimeout(() => {
                                    try {
                                        if (md && !md.classList.contains('hidden')) md.click();
                                    } catch(e2) {}
                                    blackScreenStealth(false);
                                }, 160);
                            } else {
                                blackScreenStealth(false);
                            }
                        } catch(e) {
                            blackScreenStealth(false);
                        }
                    }, 150);
                }

                if (!targetEnabled) {
                    tryWakeBlackScreenOverlayIfNeeded();
                }

                if (applyBlackScreenControl()) {
                    return "TOGGLED_IMMEDIATELY:" + targetEnabled;
                }

                const modalBefore = document.getElementById('modal');
                const wasModalHidden = !(modalBefore && !modalBefore.classList.contains('hidden'));
                let openedByUs = false;
                let tabClicked = false;

                const existingTab = findTuyChonTab() || findHienThiTab();
                if (existingTab && !wasModalHidden) {
                    realClick(existingTab);
                    tabClicked = true;
                } else {
                    const heThongBtn = findHeThongButton();
                    if (heThongBtn) {
                        openedByUs = wasModalHidden;
                        if (openedByUs) blackScreenStealth(true);
                        realClick(heThongBtn);
                    }
                }

                const deadline = Date.now() + 2600;
                const step = () => {
                    try {
                        if (applyBlackScreenControl()) {
                            closeModalIfOpenedByUs(openedByUs);
                            return;
                        }
                        if (!tabClicked) {
                            const optionTab = findTuyChonTab() || findHienThiTab();
                            if (optionTab) {
                                realClick(optionTab);
                                tabClicked = true;
                            }
                        }
                        if (Date.now() < deadline) {
                            setTimeout(step, 140);
                        } else {
                            closeModalIfOpenedByUs(openedByUs);
                        }
                    } catch(e) {
                        closeModalIfOpenedByUs(openedByUs);
                    }
                };
                setTimeout(step, 140);

                return "SEQUENCE_STARTED:" + targetEnabled;
            } catch(e) {
                return "ERROR:" + e.message;
            }
        })($targetState);
        """.trimIndent()
    }

    private const val STEALTH_BAG_HELPER_JS = """
        function norm(str) {
            return String(str || '').toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();
        }
        function noAccent(str) {
            return norm(str).normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd');
        }
        function sleep(ms) {
            return new Promise(function(r) { setTimeout(r, ms); });
        }
        function isElemShown(el, requireRect) {
            if (!el || !el.isConnected) return false;
            try {
                if ((el.type || '').toLowerCase() === 'hidden') return false;
                let cur = el;
                while (cur && cur !== document.body && cur !== document.documentElement) {
                    if (cur.hidden === true) return false;
                    if (cur.classList && cur.classList.contains('hidden')) return false;
                    if (cur.getAttribute && cur.getAttribute('aria-hidden') === 'true') return false;
                    const st = window.getComputedStyle(cur);
                    if (st && (st.display === 'none' || st.visibility === 'hidden' || st.visibility === 'collapse' || parseFloat(st.opacity) === 0)) {
                        return false;
                    }
                    cur = cur.parentElement;
                }
                if (requireRect !== false) {
                    if (el.getClientRects && el.getClientRects().length === 0) return false;
                    const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                    const hasRectSize = !!(rect && (rect.width > 1 || rect.height > 1));
                    const hasOffsetSize = (el.offsetWidth > 1 || el.offsetHeight > 1);
                    if (!hasRectSize && !hasOffsetSize) return false;
                }
            } catch (e) {}
            return true;
        }
        function isVis(el) {
            return isElemShown(el, true);
        }
        function isDisabledEl(el) {
            if (!el) return true;
            try {
                if (el.disabled === true) return true;
                if (el.hasAttribute && el.hasAttribute('disabled')) return true;
                if (el.getAttribute && el.getAttribute('aria-disabled') === 'true') return true;
                if (el.classList && el.classList.contains('disabled')) return true;
            } catch (e) {}
            return false;
        }
        function isModalOpen(md) {
            if (!md || !md.isConnected) return false;
            if (md.hidden === true || (md.classList && md.classList.contains('hidden'))) return false;
            try {
                const st = window.getComputedStyle(md);
                if (st && (st.display === 'none' || st.visibility === 'hidden' || parseFloat(st.opacity) === 0)) return false;
            } catch (e) {}
            return true;
        }
        function singleCleanClick(el) {
            if (!el) return false;
            let target = el;
            try {
                const tag = (el.tagName || '').toUpperCase();
                const isDirectInteractive = tag === 'BUTTON' || tag === 'A' || tag === 'INPUT' ||
                    el.getAttribute('role') === 'button' || el.getAttribute('role') === 'tab' ||
                    typeof el.onclick === 'function' || el.hasAttribute('onclick') ||
                    el.hasAttribute('data-id') || el.hasAttribute('data-iid') ||
                    el.hasAttribute('data-slot') || el.hasAttribute('data-item') ||
                    el.hasAttribute('data-bag') || el.hasAttribute('data-inv') ||
                    (el.classList && (el.classList.contains('btn') || el.classList.contains('bag-item') ||
                        el.classList.contains('inv-item') || el.classList.contains('item-slot') ||
                        el.classList.contains('eq-item')));
                if (!isDirectInteractive && el.closest) {
                    const anc = el.closest('button, [role="button"], [role="tab"], [role="switch"], a, input[type="button"], input[type="submit"]');
                    if (anc) target = anc;
                }
            } catch (e) {}
            if (isDisabledEl(target)) return false;
            if (typeof target.click === 'function') {
                try {
                    target.click();
                    return true;
                } catch (e) {}
            }
            try {
                target.dispatchEvent(new MouseEvent('click', { view: window, bubbles: true, cancelable: true }));
                return true;
            } catch (e) {}
            return false;
        }
        function realClick(el) {
            return singleCleanClick(el);
        }
        function ensureStealthCss() {
            try {
                const id = '__vlm_stealth_css';
                let st = document.getElementById(id);
                if (st && st.dataset && st.dataset.ver === '3') return;
                if (!st) {
                    st = document.createElement('style');
                    st.id = id;
                    (document.head || document.documentElement).appendChild(st);
                }
                if (st.dataset) st.dataset.ver = '3';
                st.textContent =
                    'html.__vlm-stealth #modal, html.__vlm-stealth #modal *, ' +
                    'html.__vlm-stealth #cModal, html.__vlm-stealth #cModal *, ' +
                    'html.__vlm-stealth #cfModal, html.__vlm-stealth #cfModal *, ' +
                    'html.__vlm-stealth #confirmModal, html.__vlm-stealth #confirmModal *, ' +
                    'html.__vlm-stealth #itemModal, html.__vlm-stealth #itemModal *, ' +
                    'html.__vlm-stealth #detailModal, html.__vlm-stealth #detailModal *, ' +
                    'html.__vlm-stealth #popup, html.__vlm-stealth #popup *, ' +
                    'html.__vlm-stealth .popup, html.__vlm-stealth .popup *, ' +
                    'html.__vlm-stealth .modal, html.__vlm-stealth .modal *, ' +
                    'html.__vlm-stealth [role="dialog"], html.__vlm-stealth [role="dialog"] *, ' +
                    'html.__vlm-stealth [aria-modal="true"], html.__vlm-stealth [aria-modal="true"] *, ' +
                    'html.__vlm-stealth .confirm, html.__vlm-stealth .confirm *, ' +
                    'html.__vlm-stealth [data-confirm], html.__vlm-stealth [data-confirm] *, ' +
                    'html.__vlm-stealth .modal-backdrop, html.__vlm-stealth .modal-overlay, html.__vlm-stealth .backdrop, html.__vlm-stealth .overlay, ' +
                    'html.__vlm-stealth #u20pop, html.__vlm-stealth #u20pop *, ' +
                    'html.__vlm-stealth .u20-pop, html.__vlm-stealth .u20-pop *, ' +
                    'html.__vlm-stealth .u20-more, html.__vlm-stealth .u20-more *, ' +
                    'html.__vlm-stealth .more-menu, html.__vlm-stealth .more-menu *, ' +
                    'html.__vlm-stealth [data-more-pop], html.__vlm-stealth [data-more-pop] *, ' +
                    'html.__vlm-stealth #t-inv, html.__vlm-stealth #t-inv *, ' +
                    'html.__vlm-stealth #t-bag, html.__vlm-stealth #t-bag *, ' +
                    'html.__vlm-stealth #p-inv, html.__vlm-stealth #p-inv *, ' +
                    'html.__vlm-stealth #p-bag, html.__vlm-stealth #p-bag *, ' +
                    'html.__vlm-stealth #invTab, html.__vlm-stealth #invTab *, ' +
                    'html.__vlm-stealth #bagTab, html.__vlm-stealth #bagTab *, ' +
                    'html.__vlm-stealth #invPane, html.__vlm-stealth #invPane *, ' +
                    'html.__vlm-stealth #bagPane, html.__vlm-stealth #bagPane *, ' +
                    'html.__vlm-stealth #invPanel, html.__vlm-stealth #invPanel *, ' +
                    'html.__vlm-stealth #bagPanel, html.__vlm-stealth #bagPanel * { ' +
                    'pointer-events: none !important; transition: none !important; animation: none !important; } ' +
                    'html.__vlm-stealth #modal, html.__vlm-stealth #cModal, html.__vlm-stealth #cfModal, ' +
                    'html.__vlm-stealth #confirmModal, html.__vlm-stealth #itemModal, html.__vlm-stealth #detailModal, ' +
                    'html.__vlm-stealth #popup, html.__vlm-stealth .popup, html.__vlm-stealth .modal, ' +
                    'html.__vlm-stealth [role="dialog"], html.__vlm-stealth [aria-modal="true"], ' +
                    'html.__vlm-stealth .confirm, html.__vlm-stealth [data-confirm], ' +
                    'html.__vlm-stealth .modal-backdrop, html.__vlm-stealth .modal-overlay, html.__vlm-stealth .backdrop, html.__vlm-stealth .overlay, ' +
                    'html.__vlm-stealth #u20pop, html.__vlm-stealth .u20-pop, html.__vlm-stealth .u20-more, ' +
                    'html.__vlm-stealth .more-menu, html.__vlm-stealth [data-more-pop], ' +
                    'html.__vlm-stealth #t-inv, html.__vlm-stealth #t-bag, html.__vlm-stealth #p-inv, html.__vlm-stealth #p-bag, ' +
                    'html.__vlm-stealth #invTab, html.__vlm-stealth #bagTab, ' +
                    'html.__vlm-stealth #invPane, html.__vlm-stealth #bagPane, html.__vlm-stealth #invPanel, html.__vlm-stealth #bagPanel { ' +
                    'opacity: 0.001 !important; z-index: -9999 !important; position: fixed !important; left: -99999px !important; top: -99999px !important; }';
            } catch (e) {}
        }
        function stealthObj() {
            if (!window.__vlmStealth) {
                window.__vlmStealth = {
                    count: 0,
                    enter: function () {
                        ensureStealthCss();
                        this.count = (this.count | 0) + 1;
                        try { document.documentElement.classList.add('__vlm-stealth'); } catch (e) {}
                    },
                    leave: function () {
                        this.count = Math.max(0, (this.count | 0) - 1);
                        if (this.count === 0) {
                            try { document.documentElement.classList.remove('__vlm-stealth'); } catch (e) {}
                        }
                    }
                };
            }
            return window.__vlmStealth;
        }
        async function acquireModalLock(ms) {
            const waitEnd = Date.now() + 8000;
            while ((window.__vlmModalLockUntil || 0) > Date.now() && Date.now() < waitEnd) {
                await sleep(110);
            }
            window.__vlmModalLockUntil = Date.now() + (ms || 25000);
        }
        function releaseModalLock() {
            window.__vlmModalLockUntil = 0;
        }
        function isAuthOrProtectedModalOpen() {
            const ids = ['ngU', 'ngP', 'ngStart', 'ngIn', 'csel', 'csGo', 'sfRe', 'ngRetry'];
            for (const id of ids) {
                const el = document.getElementById(id);
                if (el && isVis(el)) return true;
            }
            const md = document.getElementById('modal');
            if (md && isModalOpen(md)) {
                const t = noAccent(md.textContent || '');
                if (t.includes('choi o day') || t.includes('choi o tab nay') || t.includes('dang ky tai khoan') || t.includes('chon nhan vat')) {
                    return true;
                }
            }
            return false;
        }
        function isAtLoginOrSelectScreen() {
            const gateIds = ['ngU', 'ngP', 'ngStart', 'ngIn'];
            for (const id of gateIds) {
                const el = document.getElementById(id);
                if (el && isVis(el)) return 'Tab đang ở màn hình đăng nhập';
            }
            const csel = document.getElementById('csel');
            if (csel && isVis(csel)) return 'Tab đang ở màn hình chọn nhân vật';
            return null;
        }
        async function closeModalCleanly() {
            if (isAuthOrProtectedModalOpen()) return;
            const md = document.getElementById('modal');
            try {
                if (md && isModalOpen(md)) {
                    const x = document.getElementById('mClose');
                    if (x && isVis(x)) { singleCleanClick(x); }
                    const e1 = Date.now() + 400;
                    while (isModalOpen(md) && Date.now() < e1) await sleep(60);
                }
                if (md && isModalOpen(md)) {
                    try { md.click(); } catch (e) {}
                    const e2 = Date.now() + 260;
                    while (isModalOpen(md) && Date.now() < e2) await sleep(60);
                }
                const extraOverlays = document.querySelectorAll(
                    '#cModal, #cfModal, #confirmModal, #itemModal, #detailModal, .confirm, [data-confirm]'
                );
                for (const ov of extraOverlays) {
                    if (!ov || !isModalOpen(ov)) continue;
                    const closeBtn = ov.querySelector('#mClose, .close, [data-close], button, [role="button"]');
                    if (closeBtn && isVis(closeBtn)) {
                        const ct = noAccent(closeBtn.innerText || closeBtn.textContent || '');
                        if (ct === 'x' || ct === 'dong' || ct === 'dong lai' || ct === 'huy' || ct === 'khong' || closeBtn.classList.contains('close')) {
                            singleCleanClick(closeBtn);
                        }
                    }
                    if (isModalOpen(ov)) {
                        try { ov.classList.add('hidden'); } catch (e2) {}
                    }
                }
            } catch (e) {}
        }
        function findVisibleBagTabPane() {
            const paneIds = ['t-inv', 't-bag', 'p-inv', 'p-bag', 'invTab', 'bagTab', 'invPane', 'bagPane', 'invPanel', 'bagPanel'];
            for (const id of paneIds) {
                const el = document.getElementById(id);
                if (el && isVis(el)) return el;
            }
            return null;
        }
        function isBagModalContentReady() {
            const md = document.getElementById('modal');
            if (!md || !isModalOpen(md)) return false;
            if (isAuthOrProtectedModalOpen()) return false;
            const mb = document.getElementById('mBody') || md;
            const t = noAccent(mb.textContent || '');
            return t.includes('ban thua') || t.includes('ban do thua') || t.includes('hanh trang') || t.includes('tui do') ||
                !!mb.querySelector('[data-bag],[data-inv],[data-iid],.bag-item,.inv-item,#invTabs,#bagTabs');
        }
        function collectVisibleBagTriggers() {
            const md = document.getElementById('modal');
            const body = document.body;
            const isMobileU20 = !!(body && (body.classList.contains('u20') || body.classList.contains('u2m')));
            const ids = isMobileU20
                ? ['u20bag', 'bagBtn', 'tuiBtn', 'invBtn', 'bBag', 'btnBag']
                : ['bagBtn', 'u20bag', 'tuiBtn', 'invBtn', 'bBag', 'btnBag'];
            const out = [];
            function pushIfNewVisible(el) {
                if (!el || out.indexOf(el) >= 0) return;
                if (el.closest && el.closest('#chatLines, #u20lines, #logBox, #u20log, #chatBox')) return;
                if (md && md.contains(el)) return;
                if (!isVis(el) || isDisabledEl(el)) return;
                out.push(el);
            }
            for (const id of ids) {
                pushIfNewVisible(document.getElementById(id));
            }
            const attrEls = document.querySelectorAll(
                '[data-open="bag"], [data-tab="bag"], [data-tab="inv"], [data-tab="t-inv"], ' +
                '[data-tab*="inv" i], [data-tab*="bag" i], ' +
                '[data-t="inv"], [data-t="bag"], [data-nav="bag"], [data-act="bag"], ' +
                '[onclick*="inv" i], [onclick*="bag" i], [onclick*="tui" i], ' +
                '[title*="Hành trang" i], [aria-label*="Hành trang" i]'
            );
            for (const attrEl of attrEls) {
                pushIfNewVisible(attrEl);
            }
            const btns = document.querySelectorAll('button, [role="button"], [role="tab"], a, .btn, [onclick], #u20bar > *, #tabs > *');
            for (const b of btns) {
                if (b.closest && b.closest('#chatLines, #u20lines, #logBox, #u20log, #chatBox')) continue;
                if (md && md.contains(b)) continue;
                if (!isVis(b) || isDisabledEl(b)) continue;
                const bt = noAccent(b.innerText || b.textContent || b.getAttribute('title') || b.getAttribute('aria-label') || '');
                const btClean = bt.replace(/\(\s*\d+\s*(?:\/\s*\d+)?\s*\)/g, '').replace(/\s+\d+(?:\/\d+)?$/, '').trim();
                if (btClean === 'hanh trang' || btClean === 'tui do' || btClean === 'tui' ||
                    (bt.includes('hanh trang') && bt.length <= 28) ||
                    (bt.includes('tui do') && bt.length <= 24)) {
                    pushIfNewVisible(b);
                }
            }
            const leafEls = document.querySelectorAll('div, span');
            for (const l of leafEls) {
                if (l.children && l.children.length > 3) continue;
                if (l.closest && l.closest('#chatLines, #u20lines, #logBox, #u20log, #chatBox')) continue;
                if (md && md.contains(l)) continue;
                if (!isVis(l) || isDisabledEl(l)) continue;
                const lt = noAccent(l.innerText || l.textContent || '');
                if (lt === 'hanh trang' || lt === 'tui do') {
                    pushIfNewVisible(l);
                }
            }
            return out;
        }
        function findBagButtonDirect() {
            const cands = collectVisibleBagTriggers();
            return cands.length > 0 ? cands[0] : null;
        }
        function findOverflowMoreButton() {
            const md = document.getElementById('modal');
            const moreIds = ['u20more', 'moreBtn', 'btnMore'];
            for (const id of moreIds) {
                const cand = document.getElementById(id);
                if (cand && (!md || !md.contains(cand)) && isVis(cand) && !isDisabledEl(cand)) return cand;
            }
            const all = document.querySelectorAll('button, [role="button"], a, .btn, div[onclick]');
            for (const b of all) {
                if (md && md.contains(b)) continue;
                if (b.closest && b.closest('#chatLines, #u20lines, #logBox, #u20log, #chatBox')) continue;
                if (!isVis(b) || isDisabledEl(b)) continue;
                const t = noAccent(b.innerText || b.textContent || '');
                if (t === 'xem them' || t === 'them' || t === 'menu') return b;
            }
            return null;
        }
        function closeOverflowMenuIfStillOpen(moreBtn) {
            if (!moreBtn || !moreBtn.isConnected) return;
            try {
                const pop = document.querySelector('#u20pop, .u20-pop, .u20-more, .more-menu, [data-more-pop]');
                if (pop && isVis(pop)) {
                    singleCleanClick(moreBtn);
                }
            } catch (e) {}
        }
        async function findBagButtonWithOverflow() {
            let btn = findBagButtonDirect();
            if (btn) return btn;
            const moreBtn = findOverflowMoreButton();
            if (moreBtn) {
                singleCleanClick(moreBtn);
                await sleep(180);
                btn = findBagButtonDirect();
            }
            return btn;
        }
        function getActivePanelTabBtn() {
            const tabsEl = document.getElementById('tabs');
            if (tabsEl) {
                const found = tabsEl.querySelector('.on, .active, [aria-selected="true"]');
                if (found) return found;
            }
            return document.querySelector('[role="tablist"] [aria-selected="true"], .tabs .on, .tabs .active');
        }
        async function openBagContainerHidden() {
            const gateReason = isAtLoginOrSelectScreen();
            if (gateReason) return { error: gateReason };

            const md = document.getElementById('modal');
            if (md && isModalOpen(md)) {
                if (isBagModalContentReady()) {
                    return { root: document.getElementById('mBody') || md, modalEl: md, mode: 'modal', openedByUs: false };
                }
                if (isAuthOrProtectedModalOpen()) {
                    return { error: 'Tab đang có bảng xác nhận hoặc đăng nhập' };
                }
                await closeModalCleanly();
                await sleep(120);
            }

            const alreadyPane = findVisibleBagTabPane();
            if (alreadyPane && alreadyPane.querySelector('#invTabs, #bagTabs, .bag-tabs, [data-bag], [data-inv], [data-iid], [data-slot], .bag-item, .inv-item, .item-card, .item')) {
                return { root: alreadyPane, mode: 'tab', openedByUs: false, prevTabBtn: null };
            }

            async function tryCandidateList(candidates, triedSet) {
                for (const cand of candidates) {
                    if (triedSet.has(cand)) continue;
                    triedSet.add(cand);
                    const prevTabBtn = getActivePanelTabBtn();
                    if (!singleCleanClick(cand)) continue;

                    const waitEnd = Date.now() + 1400;
                    while (Date.now() < waitEnd) {
                        if (md && isModalOpen(md) && !isAuthOrProtectedModalOpen()) {
                            const bodyEnd = Date.now() + 900;
                            while (Date.now() < bodyEnd) {
                                const mb = document.getElementById('mBody') || md;
                                if (mb && ((mb.textContent || '').trim().length > 0 || (mb.children && mb.children.length > 0))) {
                                    break;
                                }
                                await sleep(70);
                            }
                            await sleep(140);
                            return {
                                root: document.getElementById('mBody') || md,
                                modalEl: md,
                                mode: 'modal',
                                openedByUs: true
                            };
                        }
                        const pane = findVisibleBagTabPane();
                        if (pane) {
                            await sleep(140);
                            return {
                                root: pane,
                                mode: 'tab',
                                openedByUs: true,
                                prevTabBtn: prevTabBtn
                            };
                        }
                        await sleep(75);
                    }
                }
                return null;
            }

            const tried = new Set();
            let directCandidates = collectVisibleBagTriggers();
            let ctx = await tryCandidateList(directCandidates, tried);
            if (ctx) return ctx;

            const moreBtn = findOverflowMoreButton();
            if (moreBtn) {
                singleCleanClick(moreBtn);
                await sleep(180);
                const overflowCandidates = collectVisibleBagTriggers();
                ctx = await tryCandidateList(overflowCandidates, tried);
                closeOverflowMenuIfStillOpen(moreBtn);
                if (ctx) return ctx;
            }

            return { error: 'Không mở được Hành trang' };
        }
        async function openBagModalHidden() {
            const res = await openBagContainerHidden();
            return (res && res.root) ? res.root : null;
        }
        async function closeBagContainerHidden(bagCtx) {
            await closeModalCleanly();
            if (bagCtx && bagCtx.mode === 'tab' && bagCtx.openedByUs && bagCtx.prevTabBtn && bagCtx.prevTabBtn.isConnected && isVis(bagCtx.prevTabBtn)) {
                singleCleanClick(bagCtx.prevTabBtn);
                await sleep(100);
            }
        }
        function normalizeSubTabLabel(rawLabel) {
            return noAccent(rawLabel || '')
                .replace(/\(\s*\d+\s*(?:\/\s*\d+)?\s*\)/g, '')
                .replace(/\s+\d+(?:\/\d+)?$/, '')
                .replace(/\s+/g, ' ')
                .trim();
        }
        async function clickBagSubTabIfPresent(mb, namesList) {
            const root = mb || document.getElementById('modal') || document.getElementById('mBody') || findVisibleBagTabPane();
            if (!root) return false;
            const tabBtns = root.querySelectorAll('button, [role="tab"], [role="button"], a');
            for (const tb of tabBtns) {
                if (!isVis(tb) || isDisabledEl(tb)) continue;
                const t = normalizeSubTabLabel(tb.innerText || tb.textContent || '');
                if (namesList.indexOf(t) >= 0) {
                    if (!tb.classList.contains('active') && !tb.classList.contains('on') && tb.getAttribute('aria-selected') !== 'true') {
                        singleCleanClick(tb);
                        await sleep(220);
                    }
                    return true;
                }
            }
            return false;
        }
        async function ensureAllBagTabIfPresent(mb) {
            return await clickBagSubTabIfPresent(mb, ['tat ca', 'all']);
        }
    """

    fun buildSellExcessItemsScript(tabIndex: Int, actionToken: String = ""): String {
        val safeToken = quote(actionToken)
        return """
        (async function(actionToken) {
            const tabIdx = $tabIndex;
            $STEALTH_BAG_HELPER_JS

            function emitExcessResult(ok, count, msg) {
                try {
                    if (window.AndroidBridge && window.AndroidBridge.onGameLog) {
                        window.AndroidBridge.onGameLog(tabIdx, msg);
                    }
                } catch (e) {}
                try {
                    if (window.AndroidBridge && window.AndroidBridge.onExcessSellResult) {
                        window.AndroidBridge.onExcessSellResult(tabIdx, actionToken || '', !!ok, count | 0, msg);
                    }
                } catch (e) {}
            }

            function findSellExcessBtn(root) {
                if (!root) return null;
                const btns = Array.from(root.querySelectorAll('button, [role="button"], a, input[type="button"], input[type="submit"], .btn, [onclick]'));
                for (const b of btns) {
                    if (b.closest && b.closest('#chatLines, #u20lines, #logBox, #u20log')) continue;
                    if (!isVis(b)) continue;
                    const t = noAccent(b.innerText || b.textContent || b.value || '');
                    if (t.includes('ban thua') || t.includes('ban do thua') || /ban\s*(do\s*)?thua/.test(t)) return b;
                }
                const leaves = Array.from(root.querySelectorAll('div, span'));
                for (const l of leaves) {
                    if (l.children && l.children.length > 2) continue;
                    if (l.closest && l.closest('#chatLines, #u20lines, #logBox, #u20log')) continue;
                    if (!isVis(l)) continue;
                    const t = noAccent(l.innerText || l.textContent || '');
                    if (t.includes('ban thua') || t.includes('ban do thua')) return l;
                }
                return null;
            }

            function isConfirmMatch(t) {
                if (!t || t.length > 34) return false;
                if (t.includes('ban thua') || t.includes('ban do thua') || t.includes('10 knb') ||
                    t === 'huy' || t === 'dong' || t === 'dong lai' || t === 'bo qua' || t === 'khong' || t === 'huy bo') return false;
                return t === 'ban' || t === 'ban het' || t === 'ban ngay' || t === 'ban tat ca' ||
                    t === 'xac nhan' || t === 'xac dinh' || t === 'dong y' || t === 'ok' ||
                    t === 'co' || t === 'tiep tuc' ||
                    /^ban(\s*\(?\d+\)?|\s+tat\s+ca|\s+het|\s+do)?$/.test(t);
            }

            function findSellConfirmBtn(excludeBtn, preExistingSet) {
                const sel = 'button, [role="button"], a, input[type="button"], input[type="submit"], .btn, [onclick], div, span';
                const overlayRoots = [
                    document.getElementById('cModal'),
                    document.getElementById('cfModal'),
                    document.getElementById('confirmModal'),
                    document.querySelector('.confirm'),
                    document.querySelector('[data-confirm]'),
                    document.querySelector('[role="dialog"]:not(#modal)'),
                    document.querySelector('.modal:not(#modal)')
                ];
                for (const r of overlayRoots) {
                    if (!r || !isVis(r)) continue;
                    const btns = Array.from(r.querySelectorAll(sel));
                    for (const b of btns) {
                        if (b === excludeBtn || isDisabledEl(b) || !isVis(b)) continue;
                        if ((b.tagName === 'DIV' || b.tagName === 'SPAN') && b.children && b.children.length > 2) continue;
                        const t = noAccent(b.innerText || b.textContent || b.value || '');
                        if (isConfirmMatch(t)) return b;
                    }
                }
                const modalRoots = [document.getElementById('mFoot'), document.getElementById('mBody'), document.getElementById('modal'), document.body];
                for (const r of modalRoots) {
                    if (!r || !isVis(r)) continue;
                    const btns = Array.from(r.querySelectorAll(sel));
                    for (const b of btns) {
                        if (b === excludeBtn || isDisabledEl(b) || !isVis(b)) continue;
                        if ((b.tagName === 'DIV' || b.tagName === 'SPAN') && b.children && b.children.length > 2) continue;
                        if (preExistingSet && preExistingSet.has(b) && !(b.closest && b.closest('#cModal, #cfModal, .confirm, [data-confirm]'))) continue;
                        const t = noAccent(b.innerText || b.textContent || b.value || '');
                        if (isConfirmMatch(t)) return b;
                    }
                }
                return null;
            }

            const origConfirm = window.confirm;
            await acquireModalLock(15000);
            stealthObj().enter();
            let bagCtx = null;
            try {
                try { window.confirm = function() { return true; }; } catch (e) {}

                bagCtx = await openBagContainerHidden();
                if (!bagCtx || !bagCtx.root) {
                    const reason = (bagCtx && bagCtx.error) ? bagCtx.error : 'Không mở được Hành trang';
                    emitExcessResult(false, 0, '⚠️ ' + reason);
                    return;
                }

                function currentBagSearchRoot() {
                    if (bagCtx.mode === 'modal') {
                        return document.getElementById('modal') || document.getElementById('mBody') || bagCtx.root;
                    }
                    return bagCtx.root;
                }

                let excessBtn = null;
                let triedSubTabs = false;
                const findEnd = Date.now() + 3400;
                while (Date.now() < findEnd) {
                    excessBtn = findSellExcessBtn(currentBagSearchRoot());
                    if (excessBtn) break;
                    if (!triedSubTabs && Date.now() > findEnd - 2400) {
                        triedSubTabs = true;
                        await ensureAllBagTabIfPresent(bagCtx.root) || await clickBagSubTabIfPresent(bagCtx.root, ['trang bi', 'do thua', 'vat pham']);
                    }
                    await sleep(110);
                }

                if (!excessBtn) {
                    emitExcessResult(false, 0, '🧹 Không tìm thấy nút Bán thừa trong Hành trang');
                    return;
                }

                const btnText = noAccent(excessBtn.innerText || excessBtn.textContent || excessBtn.value || '');
                const mCount = /(\d+)/.exec(btnText);
                const excessCount = mCount ? parseInt(mCount[1], 10) : -1;
                if (isDisabledEl(excessBtn) || excessCount === 0) {
                    emitExcessResult(true, 0, '🧹 Không có đồ thừa để bán');
                    return;
                }

                const preBtns = new Set(
                    Array.from(document.querySelectorAll('button, [role="button"], a, input[type="button"], input[type="submit"], .btn, [onclick], div, span'))
                        .filter(function(b) {
                            if ((b.tagName === 'DIV' || b.tagName === 'SPAN') && b.children && b.children.length > 2) return false;
                            return isVis(b);
                        })
                );

                if (!singleCleanClick(excessBtn)) {
                    emitExcessResult(false, 0, '⚠️ Không bấm được nút Bán thừa');
                    return;
                }

                let soldConfirmed = false;
                const waitEnd = Date.now() + 3200;
                while (Date.now() < waitEnd) {
                    await sleep(110);
                    const confirmBtn = findSellConfirmBtn(excessBtn, preBtns);
                    if (confirmBtn) {
                        singleCleanClick(confirmBtn);
                        const postEnd = Date.now() + 450;
                        while (Date.now() < postEnd) {
                            await sleep(70);
                            if (!isVis(confirmBtn)) break;
                        }
                        soldConfirmed = true;
                        break;
                    }
                    const btnAfter = findSellExcessBtn(currentBagSearchRoot());
                    if (!btnAfter || isDisabledEl(btnAfter)) {
                        soldConfirmed = true;
                        break;
                    }
                    const afterM = /(\d+)/.exec(noAccent(btnAfter.innerText || btnAfter.textContent || ''));
                    if (afterM) {
                        const afterCount = parseInt(afterM[1], 10);
                        if (afterCount === 0 || (excessCount > 0 && afterCount < excessCount)) {
                            soldConfirmed = true;
                            break;
                        }
                    }
                }

                if (soldConfirmed) {
                    const countShown = excessCount > 0 ? excessCount : 1;
                    emitExcessResult(true, countShown, '🧹 Đã bán ẩn ' + (excessCount > 0 ? excessCount + ' ' : '') + 'đồ thừa');
                } else {
                    emitExcessResult(false, 0, '⚠️ Đã bấm Bán thừa nhưng chưa thấy nút xác nhận Bán');
                }
            } catch (err) {
                emitExcessResult(false, 0, '⚠️ Lỗi bán đồ thừa: ' + (err && err.message ? err.message : 'không rõ'));
            } finally {
                try { window.confirm = origConfirm; } catch (e) {}
                await closeBagContainerHidden(bagCtx);
                stealthObj().leave();
                releaseModalLock();
            }
        })($safeToken);
        """.trimIndent()
    }
}
