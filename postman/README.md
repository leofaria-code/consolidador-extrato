# Coleção Postman da demo

**Fonte de verdade:** `consolidador-extrato.postman_collection.json` — roda **sem conta Postman** e é o que o CI e a banca executam:

```bash
npx newman run postman/consolidador-extrato.postman_collection.json   # 27 asserções
```

Pré-requisito: a stack de demo de pé (`./demo.ps1`).

## Por que JSON, e não o sync de workspace do Postman?

O repo-sync do Postman (`.postman/`, `postman/collections/**.yaml`) amarra ao workspace **de uma conta** — quem clona não consegue executá-lo — e `environments/globals` são a categoria de arquivo onde segredos acabam parando. Por isso ficam no `.gitignore`: são o "`.idea/` do Postman" — úteis localmente, ruído no git. O JSON é executável por qualquer um, diffável e automatizável (Newman/CI).

## Fluxo de edição

1. **Editar visualmente:** importe o JSON no seu Postman (File → Import) e trabalhe à vontade.
2. **Promover ao time:** exporte a coleção (Collection v2.1) por cima do JSON canônico e abra PR — o diff mostra a mudança request a request, e o workflow `e2e` valida as asserções contra a stack real.
3. Asserções novas: prefira **relativas ao estado** (ver pastas 2 e 6) — a coleção precisa passar N vezes seguidas contra a base descartável da demo.
