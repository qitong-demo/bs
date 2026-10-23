import { request } from "./http";

export function getStationMembers(stationId) {
  return request({ url: "/station/member", params: { stationId } });
}

export function addStationMember(data) {
  return request({ url: "/station/member", method: "post", data });
}

export function updateStationMemberStatus(id, status) {
  return request({ url: `/station/member/${id}/status`, method: "put", data: { status } });
}

export function removeStationMember(id) {
  return request({ url: `/station/member/${id}`, method: "delete" });
}
