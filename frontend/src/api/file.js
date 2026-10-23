import { http } from "./http";

export async function uploadFile(file) {
  const form = new FormData();
  form.append("file", file);
  const response = await http.post("/file/upload", form, {
    headers: { "Content-Type": "multipart/form-data" }
  });
  return response.data?.data || response.data;
}
