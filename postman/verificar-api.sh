#!/usr/bin/env bash
# Guarda SEMÂNTICO de frescor: compara as operações (método, path) do OpenAPI
# dos serviços rodando com as requests das coleções commitadas em postman/api.
# Byte-diff não serve aqui: o conversor gera ids/exemplos aleatórios a cada
# execução (não-determinismo comprovado em 11/07) — o invariante que importa
# é a SUPERFÍCIE da API, e é isso que se compara.
set -euo pipefail
cd "$(dirname "$0")/.."
PYBIN="$(command -v python3 || command -v python)"

"$PYBIN" - <<'PY'
import json, re, sys, urllib.request

SERVICOS = {'extrato-ingestao': 8081, 'extrato-consolidacao': 8082, 'extrato-consulta': 8083}
VERBOS = {'get', 'post', 'put', 'delete', 'patch'}
falhou = False

for svc, porta in SERVICOS.items():
    spec = json.load(urllib.request.urlopen(f'http://localhost:{porta}/q/openapi?format=json'))
    api = {(m.upper(), p) for p, ops in spec['paths'].items() for m in ops if m.lower() in VERBOS}

    col = json.load(open(f'postman/api/{svc}.postman_collection.json', encoding='utf-8'))
    reqs = set()

    def caminhar(itens):
        for it in itens:
            if 'item' in it:
                caminhar(it['item'])
                continue
            r = it['request']
            u = r['url']
            if isinstance(u, dict):
                bruto = u.get('raw') or '/' + '/'.join(u.get('path', []))
            else:
                bruto = u
            caminho = bruto.replace('{{baseUrl}}', '').split('?')[0]
            if not caminho.startswith('/'):
                caminho = '/' + caminho
            caminho = re.sub(r':([A-Za-z0-9_]+)', r'{\1}', caminho)
            reqs.add((r['method'].upper(), caminho))

    caminhar(col['item'])
    faltam, sobram = api - reqs, reqs - api
    if faltam or sobram:
        falhou = True
        print(f'[{svc}] DEFASADA — faltam na coleção: {sorted(faltam)} | sobram (não existem mais na API): {sorted(sobram)}')
    else:
        print(f'[{svc}] OK — {len(api)} operações cobertas')

sys.exit(1 if falhou else 0)
PY
