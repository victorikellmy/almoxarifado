/* Repeater genérico de linhas de formulário, dirigido por data-attributes.
   Carregado com `defer` — executa após o parse do DOM.

   - [data-repeater]                 container das linhas
   - [data-repeater-item]            cada linha repetível
   - [data-repeater-template="sel"]  (opcional, no container) <template> usado
                                     como modelo; sem ele, clona a primeira
                                     linha e restaura os valores padrão
   - [data-repeater-add="sel"]       botão que adiciona uma linha ao container `sel`
   - [data-repeater-remove]          botão de remover, dentro da linha
   - [data-repeater-last="clear"]    (opcional, no container) ao remover a última
                                     linha, limpa os campos em vez de ignorar */
(function () {
    'use strict';

    function resetarCampos(row) {
        row.querySelectorAll('input').forEach((input) => {
            input.value = input.defaultValue;
        });
        row.querySelectorAll('select').forEach((select) => {
            select.value = '';
        });
    }

    function novaLinha(container) {
        const templateSel = container.getAttribute('data-repeater-template');
        if (templateSel) {
            const template = document.querySelector(templateSel);
            return template.content.firstElementChild.cloneNode(true);
        }
        const row = container.querySelector('[data-repeater-item]').cloneNode(true);
        resetarCampos(row);
        return row;
    }

    document.querySelectorAll('[data-repeater-add]').forEach((btn) => {
        const container = document.querySelector(btn.getAttribute('data-repeater-add'));
        if (!container) return;
        btn.addEventListener('click', () => {
            container.appendChild(novaLinha(container));
        });
    });

    document.querySelectorAll('[data-repeater]').forEach((container) => {
        container.addEventListener('click', (ev) => {
            const btn = ev.target.closest('[data-repeater-remove]');
            if (!btn || !container.contains(btn)) return;
            const row = btn.closest('[data-repeater-item]');
            if (!row) return;
            if (container.querySelectorAll('[data-repeater-item]').length > 1) {
                row.remove();
            } else if (container.getAttribute('data-repeater-last') === 'clear') {
                // Última linha: limpa em vez de remover
                resetarCampos(row);
            }
        });
    });
})();
