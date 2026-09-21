// 淘宝官方API淘口令生成服务
// 使用 taobao.tbk.tpwd.mix.create 接口生成淘口令
// 环境变量: TB_APP_KEY, TB_APP_SECRET

const API_URL = 'https://eco.taobao.com/router/rest';

// TOP 签名算法
function sign(params, secret) {
    const sorted = Object.keys(params).sort();
    let str = secret;
    for (const key of sorted) {
        str += key + params[key];
    }
    str += secret;
    return md5(str).toUpperCase();
}

// MD5 实现
function add32(a, b) { return (a + b) & 0xFFFFFFFF; }
function cmn(q, a, b, x, s, t) {
    a = add32(add32(a, q), add32(x, t));
    return add32((a << s) | (a >>> (32 - s)), b);
}
function ff(a, b, c, d, x, s, t) { return cmn((b & c) | ((~b) & d), a, b, x, s, t); }
function gg(a, b, c, d, x, s, t) { return cmn((b & d) | (c & (~d)), a, b, x, s, t); }
function hh(a, b, c, d, x, s, t) { return cmn(b ^ c ^ d, a, b, x, s, t); }
function ii(a, b, c, d, x, s, t) { return cmn(c ^ (b | (~d)), a, b, x, s, t); }

function md5cycle(x, k) {
    let a = x[0], b = x[1], c = x[2], d = x[3];
    a = ff(a, b, c, d, k[0], 7, -680876936);
    d = ff(d, a, b, c, k[1], 12, -389564586);
    c = ff(c, d, a, b, k[2], 17, 606105819);
    b = ff(b, c, d, a, k[3], 22, -1044525330);
    a = ff(a, b, c, d, k[4], 7, -176418897);
    d = ff(d, a, b, c, k[5], 12, 1200080426);
    c = ff(c, d, a, b, k[6], 17, -1473231341);
    b = ff(b, c, d, a, k[7], 22, -45705983);
    a = ff(a, b, c, d, k[8], 7, 1770035416);
    d = ff(d, a, b, c, k[9], 12, -1958414417);
    c = ff(c, d, a, b, k[10], 17, -42063);
    b = ff(b, c, d, a, k[11], 22, -1990404162);
    a = ff(a, b, c, d, k[12], 7, 1804603682);
    d = ff(d, a, b, c, k[13], 12, -40341101);
    c = ff(c, d, a, b, k[14], 17, -1502002290);
    b = ff(b, c, d, a, k[15], 22, 1236535329);
    a = gg(a, b, c, d, k[1], 5, -165796510);
    d = gg(d, a, b, c, k[6], 9, -1069501632);
    c = gg(c, d, a, b, k[11], 14, 643717713);
    b = gg(b, c, d, a, k[0], 20, -373897302);
    a = gg(a, b, c, d, k[5], 5, -701558691);
    d = gg(d, a, b, c, k[10], 9, 38016083);
    c = gg(c, d, a, b, k[15], 14, -660478335);
    b = gg(b, c, d, a, k[4], 20, -405537848);
    a = gg(a, b, c, d, k[9], 5, 568446438);
    d = gg(d, a, b, c, k[14], 9, -1019803690);
    c = gg(c, d, a, b, k[3], 14, -187363961);
    b = gg(b, c, d, a, k[8], 20, 1163531501);
    a = gg(a, b, c, d, k[13], 5, -1444681467);
    d = gg(d, a, b, c, k[2], 9, -51403784);
    c = gg(c, d, a, b, k[7], 14, 1735328473);
    b = gg(b, c, d, a, k[12], 20, -1926607734);
    a = hh(a, b, c, d, k[5], 4, -378558);
    d = hh(d, a, b, c, k[8], 11, -2022574463);
    c = hh(c, d, a, b, k[11], 16, 1839030562);
    b = hh(b, c, d, a, k[14], 23, -35309556);
    a = hh(a, b, c, d, k[1], 4, -1530992060);
    d = hh(d, a, b, c, k[4], 11, 1272893353);
    c = hh(c, d, a, b, k[7], 16, -155497632);
    b = hh(b, c, d, a, k[10], 23, -1094730640);
    a = hh(a, b, c, d, k[13], 4, 681279174);
    d = hh(d, a, b, c, k[0], 11, -358537222);
    c = hh(c, d, a, b, k[3], 16, -722521979);
    b = hh(b, c, d, a, k[6], 23, 76029189);
    a = hh(a, b, c, d, k[9], 4, -640364487);
    d = hh(d, a, b, c, k[12], 11, -421815835);
    c = hh(c, d, a, b, k[15], 16, 530742520);
    b = hh(b, c, d, a, k[2], 23, -995338651);
    a = ii(a, b, c, d, k[0], 6, -198630844);
    d = ii(d, a, b, c, k[7], 10, 1126891415);
    c = ii(c, d, a, b, k[14], 15, -1416354905);
    b = ii(b, c, d, a, k[5], 21, -57434055);
    a = ii(a, b, c, d, k[12], 6, 1700485571);
    d = ii(d, a, b, c, k[3], 10, -1894986606);
    c = ii(c, d, a, b, k[10], 15, -1051523);
    b = ii(b, c, d, a, k[1], 21, -2054922799);
    a = ii(a, b, c, d, k[8], 6, 1873313359);
    d = ii(d, a, b, c, k[15], 10, -30611744);
    c = ii(c, d, a, b, k[6], 15, -1560198380);
    b = ii(b, c, d, a, k[13], 21, 1309151649);
    a = ii(a, b, c, d, k[4], 6, -145523070);
    d = ii(d, a, b, c, k[11], 10, -1120210379);
    c = ii(c, d, a, b, k[2], 15, 718787259);
    b = ii(b, c, d, a, k[9], 21, -343485551);
    x[0] = add32(a, x[0]);
    x[1] = add32(b, x[1]);
    x[2] = add32(c, x[2]);
    x[3] = add32(d, x[3]);
}

function md5blk(s) {
    const md5blks = [];
    for (let i = 0; i < 64; i += 4) {
        md5blks[i >> 2] = s.charCodeAt(i) + (s.charCodeAt(i + 1) << 8) + (s.charCodeAt(i + 2) << 16) + (s.charCodeAt(i + 3) << 24);
    }
    return md5blks;
}

function md51(s) {
    const state = [1732584193, -271733879, -1732584194, 271733878];
    let i;
    for (i = 64; i <= s.length; i += 64) {
        md5cycle(state, md5blk(s.substring(i - 64, i)));
    }
    s = s.substring(i - 64);
    const tail = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    for (i = 0; i < s.length; i++) {
        tail[i >> 2] |= s.charCodeAt(i) << ((i % 4) << 3);
    }
    tail[i >> 2] |= 0x80 << ((i % 4) << 3);
    if (i > 55) {
        md5cycle(state, tail);
        for (i = 0; i < 16; i++) tail[i] = 0;
    }
    tail[14] = s.length * 8;
    md5cycle(state, tail);
    return state;
}

function md5(s) {
    const hexChr = '0123456789abcdef';
    const x = md51(s);
    let str = '';
    for (let i = 0; i < 4; i++) {
        str += hexChr.charAt((x[i] >> 4) & 0x0F) + hexChr.charAt(x[i] & 0x0F);
        str += hexChr.charAt((x[i] >> 12) & 0x0F) + hexChr.charAt((x[i] >> 8) & 0x0F);
        str += hexChr.charAt((x[i] >> 20) & 0x0F) + hexChr.charAt((x[i] >> 16) & 0x0F);
        str += hexChr.charAt((x[i] >> 28) & 0x0F) + hexChr.charAt((x[i] >> 24) & 0x0F);
    }
    return str;
}

export async function onRequest(context) {
    const { request, env } = context;
    
    const corsHeaders = {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Content-Type': 'application/json; charset=utf-8'
    };

    if (request.method === 'OPTIONS') {
        return new Response('', { headers: corsHeaders });
    }

    try {
        const appKey = env.TB_APP_KEY;
        const appSecret = env.TB_APP_SECRET;

        if (!appKey || !appSecret) {
            return new Response(JSON.stringify({
                success: false,
                error: '服务未配置：缺少淘宝API密钥'
            }), { status: 500, headers: corsHeaders });
        }

        // 获取请求参数
        let urlParam = '';
        let textParam = '肥料任务';
        let passwordParam = '肥料口令';
        let logoParam = '';

        if (request.method === 'POST') {
            const body = await request.json();
            urlParam = body.url || '';
            textParam = body.text || textParam;
            passwordParam = body.password || passwordParam;
            logoParam = body.logo || logoParam;
        } else {
            const sp = new URL(request.url).searchParams;
            urlParam = sp.get('url') || '';
            textParam = sp.get('text') || textParam;
            passwordParam = sp.get('password') || passwordParam;
            logoParam = sp.get('logo') || logoParam;
        }

        if (!urlParam) {
            return new Response(JSON.stringify({
                success: false,
                error: '缺少 url 参数'
            }), { status: 400, headers: corsHeaders });
        }

        // 组装淘宝 API 请求参数
        const now = new Date();
        const timestamp = now.getFullYear() + '-' +
            String(now.getMonth() + 1).padStart(2, '0') + '-' +
            String(now.getDate()).padStart(2, '0') + ' ' +
            String(now.getHours()).padStart(2, '0') + ':' +
            String(now.getMinutes()).padStart(2, '0') + ':' +
            String(now.getSeconds()).padStart(2, '0');

        const params = {
            method: 'taobao.tbk.tpwd.mix.create',
            app_key: appKey,
            timestamp: timestamp,
            format: 'json',
            v: '2.0',
            sign_method: 'md5',
            simplify: 'true',
            ext: '{}',
            url: urlParam,
            text: textParam,
            password: passwordParam
        };

        if (logoParam) {
            params.logo = logoParam;
        }

        // 签名
        params.sign = sign(params, appSecret);

        // 发送请求
        const formBody = new URLSearchParams(params).toString();
        const resp = await fetch(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: formBody
        });

        const result = await resp.json();
        
        // 解析返回
        const response = result['tbk_tpwd_mix_create_response'];
        if (response && response.data) {
            const data = response.data;
            return new Response(JSON.stringify({
                success: true,
                model: data.model || '',
                passwordSimple: data.password_simple || '',
                password: data.password || '',
                shortUrl: data.short_url || data.shortUrl || '',
                data: data
            }), { headers: corsHeaders });
        } else {
            const errorResp = result.error_response;
            const errorMsg = errorResp ? (errorResp.msg + (errorResp.sub_msg ? ' - ' + errorResp.sub_msg : '')) : '未知错误';
            return new Response(JSON.stringify({
                success: false,
                error: errorMsg,
                raw: result
            }), { status: 500, headers: corsHeaders });
        }

    } catch (e) {
        return new Response(JSON.stringify({
            success: false,
            error: e.message
        }), { status: 500, headers: corsHeaders });
    }
}
