import { request } from "./http";

export function createStationEvaluation(data) {
  return request({ url: "/evaluation/station", method: "post", data });
}

export function getStationEvaluationPage(params = {}) {
  return request({ url: "/evaluation/station/page", params: { current: 1, size: 10, ...params } });
}

export function getStationEvaluationSummary(stationId) {
  return request({ url: "/evaluation/station/summary", params: { stationId } });
}

export function getMyEvaluations() {
  return request({ url: "/evaluation/my" });
}
