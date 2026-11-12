/*
 * EDAM progressive enhancement only.
 * No business logic lives here: every rule is enforced on the server. This file exists to
 * prove the static-resource pipeline and to host future accessibility helpers.
 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        // Give keyboard users an immediate way to reach the content region.
        var skipLink = document.querySelector('.edam-skip-link');
        if (skipLink) {
            skipLink.addEventListener('click', function () {
                var main = document.getElementById('edam-main');
                if (main) {
                    main.setAttribute('tabindex', '-1');
                    main.focus();
                }
            });
        }
    });
})();
