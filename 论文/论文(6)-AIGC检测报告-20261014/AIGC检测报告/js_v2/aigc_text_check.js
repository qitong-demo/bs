$(function () {
  $(
    ".top-nav__tab-li-icon-unlock,.top-nav__tab-li-icon-lock,.top-nav__tab-li-unlock,.top-nav__tab-li-lock"
  ).css({ display: "none" });

  if (typeof aigcReportLock !== "undefined" && aigcReportLock) {
    $(".top-nav__tab-li-icon-unlock,.top-nav__tab-li-unlock").css({
      display: "inline-block",
    });
    if (typeof aigcReportSimilarity !== "undefined") {
      $(".top-nav__tab--right .top-nav__tab-li-num").text(aigcReportSimilarity);
    }
    if (typeof aigcReportUrl !== "undefined") {
      $(".top-nav__tab--right").click(function () {
        window.location.href = aigcReportUrl;
      });
    }
    $(".top-nav").removeClass("top-nav-lock");
  } else {
    $(".top-nav__tab-li-icon-lock,.top-nav__tab-li-lock").css({
      display: "inline-block",
    });
    $(".top-nav").addClass("top-nav-lock");
  }

  if (typeof zipReportUrl !== "undefined" && zipReportUrl !== "") {
    $(".rptDownload").attr("href", zipReportUrl);
  }
});
