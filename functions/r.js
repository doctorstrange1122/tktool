// 短链跳转服务（v2）：
//  - ?t=<加密token> -> 服务端返回解密中转页（AES-256-GCM 解密 + tbopen 唤起手淘，明文链接不出现）
//  - ?i=&d=&s=      -> 旧格式短链，保留 302 重定向兼容
const RELAY_HTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>诊断页</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif; padding: 24px; background: #fff; color: #333; font-size: 14px; line-height: 1.6; }
  #out { white-space: pre-wrap; word-break: break-all; }
</style>
</head>
<body>
<div id="out">loading...</div>
<script>
document.getElementById("out").textContent = "JS executing...";
try {
  var ua = navigator.userAgent || "no UA";
  var search = location.search || "no search";
  var href = location.href || "no href";
  var hasURLSearchParams = typeof URLSearchParams !== "undefined" ? "yes" : "no";
  var hasTextDecoder = typeof TextDecoder !== "undefined" ? "yes" : "no";
  var hasAtob = typeof atob !== "undefined" ? "yes" : "no";
  document.getElementById("out").textContent = [
    "href: " + href,
    "search: " + search,
    "URLSearchParams: " + hasURLSearchParams,
    "TextDecoder: " + hasTextDecoder,
    "atob: " + hasAtob,
    "UA: " + ua
  ].join("\n");
} catch (e) {
  document.getElementById("out").textContent = "ERROR: " + e.message;
}
</script>
</body>
</html>
`;

export async function onRequest(context) {
    const { request } = context;
    const url = new URL(request.url);

    // 新格式：加密 token -> 返回中转页 HTML
    const token = url.searchParams.get('t');
    if (token) {
        return new Response(RELAY_HTML, {
            headers: {
                'content-type': 'text/html; charset=utf-8',
                'cache-control': 'no-store'
            }
        });
    }

    // 旧格式：i/d/s 参数 -> 拼接后 302 重定向
    const itemId = url.searchParams.get('i') || '';
    const deliveryId = url.searchParams.get('d') || '';
    const sceneId = url.searchParams.get('s') || '';
    const prismTrace = url.searchParams.get('p') || '';
    const spmb = url.searchParams.get('b') || '46023237';

    if (!itemId || !deliveryId || !sceneId) {
        return new Response('参数不完整', { status: 400 });
    }

    const targetUrl = `https://pages-fast.m.taobao.com/wow/z/app/ltao-fe/tbms-cooperation/home?&deliveryId=${deliveryId}&disableProgress=true&_tbScancodeApproach_=scan&disableNav=YES&sceneId=${sceneId}&scene=wt&hd_from_id=100085&shareurl=true&itemIds=${itemId}&forceThemis=true&x-sec=wua&un_site=0&share_crt_v=1&spm=farm.13840689.tasklist-pentaprism.101028&x-preload=true&taskhubType=taskhubcc&x-ssr=true&spma=farm&sourceType=other&taskFrom=wulengjing&prismTrace=${prismTrace}&spmb=${spmb}`;

    return Response.redirect(targetUrl, 302);
}
