// Cloudflare Pages Function: CORS 代理
// 用于解析淘宝短链，绕过浏览器 CORS 限制
// 用法: https://tktool.pages.dev/api/proxy?url=<target_url>
export async function onRequest(context) {
    const url = new URL(context.request.url);
    const targetUrl = url.searchParams.get('url');

    if (!targetUrl) {
        return new Response(JSON.stringify({ error: 'Missing url parameter' }), {
            status: 400,
            headers: {
                'Access-Control-Allow-Origin': '*',
                'Content-Type': 'application/json'
            }
        });
    }

    try {
        const response = await fetch(targetUrl, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36'
            },
            redirect: 'follow'
        });

        const finalUrl = response.url;
        const body = await response.text();

        return new Response(JSON.stringify({
            url: finalUrl,
            status: response.status,
            body: body
        }), {
            headers: {
                'Access-Control-Allow-Origin': '*',
                'Content-Type': 'application/json'
            }
        });
    } catch (e) {
        return new Response(JSON.stringify({ error: e.message }), {
            status: 500,
            headers: {
                'Access-Control-Allow-Origin': '*',
                'Content-Type': 'application/json'
            }
        });
    }
}
