// Spoilers: ||text|| and spoiler blocks are blacked out until clicked, as on Discord. A click shows that one; a
// spoiler block's chapter shows its marked name everywhere on the page too (data-spoiler groups them).
function quantimiumSpoilers() {
  document.querySelectorAll(".spoiler, .spoiler-block").forEach(function (spoiler) {
    if (spoiler.dataset.ready) return;
    spoiler.dataset.ready = "1";
    spoiler.addEventListener("click", function (event) {
      if (spoiler.classList.contains("shown")) return;
      event.preventDefault();
      event.stopPropagation();
      var group = spoiler.dataset.spoiler;
      var all = group ? document.querySelectorAll('[data-spoiler="' + group + '"]') : [spoiler];
      all.forEach(function (each) { each.classList.add("shown"); });
    }, true);
  });
}
if (typeof document$ !== "undefined") document$.subscribe(quantimiumSpoilers);
else document.addEventListener("DOMContentLoaded", quantimiumSpoilers);
