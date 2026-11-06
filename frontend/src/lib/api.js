// AI-assisted API client with Codex. Session cookies stay HttpOnly; CSRF token comes from the server.
let csrfToken = "";
export async function session() {
  const data = await request("/auth/session");
  csrfToken = data.csrfToken;
  return data.user;
}
export async function request(path, options = {}) {
  const method = options.method || "GET";
  if (!["GET", "HEAD"].includes(method) && !csrfToken) await session();
  const response = await fetch("/api" + path, {
    credentials: "same-origin",
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(!["GET", "HEAD"].includes(method)
        ? { "X-XSRF-TOKEN": csrfToken }
        : {}),
      ...options.headers,
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.detail || "请求暂时未完成，请稍后重试。");
    error.status = response.status;
    throw error;
  }
  return data;
}
