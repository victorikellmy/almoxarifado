/* Seletor de material com busca (compras de ESTOQUE).

   Uso: <div data-material-picker>
            <input type="hidden" name="itemMaterialId" class="picker-id">
            <input type="text" class="form-control picker-busca" autocomplete="off">
            <div class="picker-lista d-none"></div>
            <div class="picker-selecionado form-text"></div>
        </div>
   Catálogo: window.COMPRAS_CATALOGO.materiais = [{id, nome, sku, unidade, estoque, minimo, grupo, subgrupo}].

   Digitar filtra por nome, SKU, grupo ou subgrupo (sem acentos, qualquer ordem
   das palavras). Setas/Enter escolhem, Esc fecha. Linhas adicionadas depois
   (repeater) são inicializadas por `MaterialPicker.init(raiz)`. */
(function () {
    'use strict';

    const LIMITE = 40;

    function normalizar(txt) {
        return (txt || '').toString().normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    }

    function catalogo() {
        return (window.COMPRAS_CATALOGO && window.COMPRAS_CATALOGO.materiais) || [];
    }

    function indexar(m) {
        if (!m._busca) {
            m._busca = normalizar([m.nome, m.sku, m.grupo, m.subgrupo, m.unidade].filter(Boolean).join(' '));
        }
        return m._busca;
    }

    function filtrar(termo) {
        const palavras = normalizar(termo).split(/\s+/).filter(Boolean);
        const todos = catalogo();
        if (palavras.length === 0) return todos.slice(0, LIMITE);
        const out = [];
        for (const m of todos) {
            const texto = indexar(m);
            if (palavras.every((p) => texto.includes(p))) {
                out.push(m);
                if (out.length >= LIMITE) break;
            }
        }
        return out;
    }

    function escapar(txt) {
        return (txt == null ? '' : String(txt))
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function porId(id) {
        if (!id) return null;
        return catalogo().find((m) => String(m.id) === String(id)) || null;
    }

    function montarOpcao(m, ativo) {
        const alerta = m.estoque != null && m.minimo != null && m.estoque <= m.minimo;
        return '<div class="picker-opcao' + (ativo ? ' ativo' : '') + '" role="option" data-id="' + m.id + '">'
            + '<div class="picker-opcao-nome">' + escapar(m.nome)
            + (m.unidade ? ' <span class="text-muted">(' + escapar(m.unidade) + ')</span>' : '') + '</div>'
            + '<div class="picker-opcao-meta">'
            + (m.sku ? '<span class="font-monospace">' + escapar(m.sku) + '</span>' : '')
            + (m.grupo || m.subgrupo ? '<span>' + escapar([m.grupo, m.subgrupo].filter(Boolean).join(' › ')) + '</span>' : '')
            + (m.estoque != null ? '<span class="' + (alerta ? 'text-warning' : '') + '">estoque: ' + m.estoque + '</span>' : '')
            + '</div></div>';
    }

    function init(raiz) {
        (raiz || document).querySelectorAll('[data-material-picker]').forEach((el) => {
            if (el._pickerPronto) return;
            el._pickerPronto = true;

            const hid = el.querySelector('.picker-id');
            const inp = el.querySelector('.picker-busca');
            const lista = el.querySelector('.picker-lista');
            const info = el.querySelector('.picker-selecionado');
            if (!hid || !inp || !lista) return;

            let resultados = [];
            let indice = -1;
            let timerBlur = null;

            function mostrarSelecionado(m) {
                if (!info) return;
                if (!m) { info.textContent = ''; info.classList.add('d-none'); return; }
                info.classList.remove('d-none');
                info.innerHTML = '<i class="bi bi-check2-circle text-success"></i> '
                    + (m.sku ? '<span class="font-monospace">' + escapar(m.sku) + '</span> · ' : '')
                    + escapar([m.grupo, m.subgrupo].filter(Boolean).join(' › '))
                    + (m.estoque != null ? ' · estoque atual ' + m.estoque : '');
            }

            function selecionar(m) {
                hid.value = m ? m.id : '';
                inp.value = m ? m.nome : inp.value;
                inp.setCustomValidity('');
                el.classList.toggle('tem-selecao', !!m);
                mostrarSelecionado(m);
                fechar();
                hid.dispatchEvent(new Event('change', { bubbles: true }));
            }

            function limparSelecao() {
                if (hid.value) {
                    hid.value = '';
                    el.classList.remove('tem-selecao');
                    mostrarSelecionado(null);
                    hid.dispatchEvent(new Event('change', { bubbles: true }));
                }
            }

            /* A lista usa position:fixed para escapar do overflow da .table-responsive;
               a posição é recalculada a cada abertura, rolagem e redimensionamento. */
            function posicionar() {
                const r = inp.getBoundingClientRect();
                lista.style.left = r.left + 'px';
                lista.style.width = r.width + 'px';
                const espacoAbaixo = window.innerHeight - r.bottom;
                if (espacoAbaixo < 200 && r.top > espacoAbaixo) {
                    lista.style.top = '';
                    lista.style.bottom = (window.innerHeight - r.top + 4) + 'px';
                    lista.style.maxHeight = Math.min(320, r.top - 16) + 'px';
                } else {
                    lista.style.bottom = '';
                    lista.style.top = (r.bottom + 4) + 'px';
                    lista.style.maxHeight = Math.min(320, espacoAbaixo - 16) + 'px';
                }
            }

            function render() {
                posicionar();
                if (resultados.length === 0) {
                    lista.innerHTML = '<div class="picker-vazio">Nenhum material encontrado. '
                        + 'Confira a grafia ou cadastre o material em <strong>Materiais</strong>.</div>';
                } else {
                    lista.innerHTML = resultados.map((m, i) => montarOpcao(m, i === indice)).join('');
                }
                lista.classList.remove('d-none');
            }

            function abrir() {
                resultados = filtrar(inp.value);
                indice = resultados.length ? 0 : -1;
                render();
            }

            function fechar() {
                lista.classList.add('d-none');
                indice = -1;
            }

            const reposicionar = () => { if (!lista.classList.contains('d-none')) posicionar(); };
            window.addEventListener('scroll', reposicionar, true);
            window.addEventListener('resize', reposicionar);

            inp.addEventListener('focus', abrir);
            inp.addEventListener('input', () => {
                limparSelecao();
                abrir();
            });
            inp.addEventListener('keydown', (ev) => {
                if (lista.classList.contains('d-none') && (ev.key === 'ArrowDown' || ev.key === 'ArrowUp')) {
                    abrir(); ev.preventDefault(); return;
                }
                if (ev.key === 'ArrowDown') {
                    if (resultados.length) { indice = (indice + 1) % resultados.length; render(); }
                    ev.preventDefault();
                } else if (ev.key === 'ArrowUp') {
                    if (resultados.length) { indice = (indice - 1 + resultados.length) % resultados.length; render(); }
                    ev.preventDefault();
                } else if (ev.key === 'Enter') {
                    if (!lista.classList.contains('d-none') && indice >= 0 && resultados[indice]) {
                        selecionar(resultados[indice]);
                        ev.preventDefault();          // não submete o form ao escolher
                    }
                } else if (ev.key === 'Escape') {
                    fechar();
                } else if (ev.key === 'Tab') {
                    // Tab com um único resultado = aceita
                    if (!lista.classList.contains('d-none') && resultados.length === 1) selecionar(resultados[0]);
                    fechar();
                }
            });
            inp.addEventListener('blur', () => {
                timerBlur = setTimeout(() => {
                    fechar();
                    // Texto digitado sem escolher um material: desfaz para não enganar o usuário
                    if (!hid.value && inp.value.trim() !== '') {
                        const exato = catalogo().find((m) => normalizar(m.nome) === normalizar(inp.value));
                        if (exato) selecionar(exato);
                    }
                }, 150);
            });
            lista.addEventListener('mousedown', (ev) => {
                ev.preventDefault();                  // mantém o foco no input
                const op = ev.target.closest('.picker-opcao');
                if (!op) return;
                const m = porId(op.getAttribute('data-id'));
                if (m) { clearTimeout(timerBlur); selecionar(m); }
            });

            // Obrigatoriedade: o hidden não valida; validamos pelo campo de busca.
            inp.addEventListener('invalid', () => {
                if (inp.required && !hid.value) inp.setCustomValidity('Escolha um material da lista.');
            });
            const form = el.closest('form');
            if (form) {
                form.addEventListener('submit', () => {
                    if (inp.required && !inp.disabled && !hid.value) {
                        inp.setCustomValidity('Escolha um material da lista.');
                    } else {
                        inp.setCustomValidity('');
                    }
                }, true);
            }

            // Pré-seleção (leitura do anexo / reenvio do form com erro)
            const inicial = porId(hid.value);
            if (inicial) selecionar(inicial);
            else if (!hid.value) mostrarSelecionado(null);
        });
    }

    window.MaterialPicker = { init: init, filtrar: filtrar, normalizar: normalizar };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => init(document));
    } else {
        init(document);
    }
})();
