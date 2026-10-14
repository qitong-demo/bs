$(function () {
  $(
    ".top-nav__tab-li-icon-unlock,.top-nav__tab-li-icon-lock,.top-nav__tab-li-unlock,.top-nav__tab-li-lock"
  ).css({ display: "none" });

  if (typeof reportLock !== "undefined" && reportLock) {
    $(".top-nav__tab-li-icon-unlock,.top-nav__tab-li-unlock").css({
      display: "inline-block",
    });
    if (typeof reportSimilarity !== "undefined") {
      $(".top-nav__tab--left .top-nav__tab-li-num").text(reportSimilarity);
    }
    if (typeof reportUrl !== "undefined") {
      $(".top-nav__tab--left").click(function () {
        window.location.href = reportUrl;
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
