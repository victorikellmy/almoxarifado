/* Comportamentos do layout do Almoxarifado (movido de fragments/layout.html).
   Carregado com `defer` — executa após o parse do DOM. */

// ---------- Theme toggle ----------
(function () {
    const btn = document.getElementById('themeToggleBtn');
    if (!btn) return;
    const iconDark = document.getElementById('themeIconDark');
    const iconLight = document.getElementById('themeIconLight');
    const sync = () => {
        const dark = document.documentElement.getAttribute('data-theme') === 'dark';
        iconDark.style.display = dark ? 'none' : '';
        iconLight.style.display = dark ? '' : 'none';
    };
    sync();
    btn.addEventListener('click', () => {
        const current = document.documentElement.getAttribute('data-theme');
        const next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        document.documentElement.setAttribute('data-bs-theme', next);
        localStorage.setItem('alm-theme', next);
        sync();
    });
})();

// ---------- Sidebar: collapse desktop + drawer mobile ----------
(function () {
    const root = document.documentElement;
    const sidebar = document.getElementById('appSidebar');
    const backdrop = document.getElementById('sidebarBackdrop');
    const openBtn = document.getElementById('sidebarOpenBtn');
    const toggleBtn = document.getElementById('sidebarToggleBtn');
    const collapseBtn = document.getElementById('sidebarCollapseBtn');

    const toggleCollapsed = () => {
        root.classList.toggle('sidebar-collapsed');
        localStorage.setItem('alm-sidebar',
            root.classList.contains('sidebar-collapsed') ? 'collapsed' : 'expanded');
    };

    const openMobile = () => {
        sidebar?.classList.add('is-open');
        backdrop?.classList.add('is-visible');
        document.body.style.overflow = 'hidden';
    };
    const closeMobile = () => {
        sidebar?.classList.remove('is-open');
        backdrop?.classList.remove('is-visible');
        document.body.style.overflow = '';
    };

    openBtn?.addEventListener('click', openMobile);
    backdrop?.addEventListener('click', closeMobile);
    toggleBtn?.addEventListener('click', toggleCollapsed);
    collapseBtn?.addEventListener('click', toggleCollapsed);

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && sidebar?.classList.contains('is-open')) closeMobile();
    });
    window.addEventListener('resize', () => {
        if (window.innerWidth >= 992) closeMobile();
    });
})();

// ---------- Toasts: auto-hide + fechar ----------
(function () {
    document.querySelectorAll('.toast-modern').forEach((toast) => {
        const delay = parseInt(toast.dataset.toastAutohide || '0', 10);
        const close = () => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(20px)';
            toast.style.transition = 'all 200ms ease-out';
            setTimeout(() => toast.remove(), 200);
        };
        toast.querySelector('.toast-close')?.addEventListener('click', close);
        if (delay > 0) setTimeout(close, delay);
    });
})();
