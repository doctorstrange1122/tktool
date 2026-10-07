// huafei 自定义链接保存接口
// POST /api/huafei-save  body: { url, key }
// 校验：url 必须是 https://pages-fast.m.taobao.com/ 开头；key 必须是 self_ 开头且合法
// 存储：KEY_STORE 的 hfself:<key> = url，30 天过期

const CORS = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
    'Content-Type': 'application/json; charset=utf-8'
};

export async function onRequest(context) {
    const { request, env } = context;

    if (request.method === 'OPTIONS') {
        return new Response('', { headers: CORS });
    }

    if (request.method !== 'POST') {
        return new Response(JSON.stringify({ success: false, message: '仅支持 POST 请求' }), {
            status: 405,
            headers: CORS
        });
    }

    try {
        const body = await request.json();
        const url = (body.url || '').trim();
        const key = (body.key || '').trim();

        if (!/^https:\/\/pages-fast\.m\.taobao\.com\//i.test(url)) {
            return new Response(JSON.stringify({ success: false, message: '只支持淘宝 pages-fast.m.taobao.com 链接' }), {
                status: 400,
                headers: CORS
            });
        }

        if (!/^self_[a-z0-9]{6,}$/i.test(key)) {
            return new Response(JSON.stringify({ success: false, message: '非法 key' }), {
                status: 400,
                headers: CORS
            });
        }

        const KV = env.KEY_STORE;
        if (!KV) {
            return new Response(JSON.stringify({ success: false, message: 'KV 未配置' }), {
                status: 500,
                headers: CORS
            });
        }

        await KV.put('hfself:' + key, url, { expirationTtl: 2592000 });

        return new Response(JSON.stringify({ success: true }), { headers: CORS });
    } catch (e) {
        return new Response(JSON.stringify({ success: false, message: e.message }), {
            status: 500,
            headers: CORS
        });
    }
}
