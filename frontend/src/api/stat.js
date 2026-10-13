import { request, downloadCsv } from "./http";

export function getStatOverview() {
  return request({ url: "/stat/overview" });
}

export function getMonthlyTrend() {
  return request({ url: "/stat/appeal/month" });
}

export function getServiceRanking() {
  return request({ url: "/stat/service/ranking" });
}

/** 报表导出类型：appeals、appointments、stations、signups、evaluations。 */
export function exportReport(type) {
  return downloadCsv(type, `${type}.csv`);
}
