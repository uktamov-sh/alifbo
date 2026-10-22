// Переключатель языка uz/ru. Выбор запоминается только в этом браузере.
(function () {
  var root = document.documentElement;
  var saved = null;
  try { saved = localStorage.getItem("alifbo-lang"); } catch (e) {}
  var lang = saved || ((navigator.language || "").toLowerCase().indexOf("ru") === 0 ? "ru" : "uz");
  function set(l) {
    root.lang = l;
    document.querySelectorAll(".langs button").forEach(function (b) {
      b.setAttribute("aria-pressed", String(b.dataset.lang === l));
    });
    try { localStorage.setItem("alifbo-lang", l); } catch (e) {}
  }
  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll(".langs button").forEach(function (b) {
      b.addEventListener("click", function () { set(b.dataset.lang); });
    });
    set(lang);
  });
})();
