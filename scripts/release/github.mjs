import assert from "node:assert/strict";

export function github(env, fetchResponse = fetch) {
  assert.ok(env.GH_TOKEN && /^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY), "Missing GitHub configuration");
  const repository = env.GITHUB_REPOSITORY;
  async function request(method, path, body, filename) {
    const upload = filename !== undefined;
    const origin = upload ? "https://uploads.github.com" : "https://api.github.com";
    const query = upload ? `?name=${encodeURIComponent(filename)}` : "";
    const response = await fetchResponse(`${origin}/repos/${repository}${path}${query}`, {
      method, redirect: "error",
      headers: {
        Authorization: `Bearer ${env.GH_TOKEN}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        ...(body ? { "Content-Type": upload ? "application/octet-stream" : "application/json" } : {}),
      },
      ...(body ? { body: upload ? body : JSON.stringify(body) } : {}),
    });
    if (!response.ok) throw new Error(`GitHub ${method} ${path} failed (${response.status})`);
    return response.status === 204 ? undefined : response.json();
  }
  return {
    request,
    upload: (id, filename, bytes) => request("POST", `/releases/${id}/assets`, bytes, filename),
    async list(path, key) {
      const results = [];
      for (let page = 1; ; page++) {
        const response = await request("GET", `${path}${path.includes("?") ? "&" : "?"}per_page=100&page=${page}`);
        const items = key ? response[key] : response;
        assert.ok(Array.isArray(items), "Unexpected GitHub list response");
        results.push(...items);
        if (items.length < 100) return results;
      }
    },
  };
}
