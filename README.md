# RAMdex API - Aplicação memory-bound

Motor de busca em memória baseado em **índice invertido**.

Todo o índice (termos → documentos + posições) e o conteúdo dos documentos ficam na RAM. O consumo de memória
cresce na proporção do volume indexado, e a busca é barata em CPU (consulta em tabela hash + interseção de
listas). Por isso o desempenho depende da memória disponível: com pouco heap, o coletor de lixo (GC) passa a
dominar o tempo de execução, a latência sobe e, no limite, ocorre `OutOfMemoryError`.

A aplicação foi implementada em **Java puro**: servidor HTTP, roteamento, JSON e multipart usam apenas o JDK. O único artefato
externo é o JUnit, usado só nos testes.

## Requisitos

- JDK 17 ou superior (`java -version`)
- Maven 3.6+ (`mvn -version`), apenas para compilar

## Compilar e executar

```bash
mvn package # compila, roda os testes e gera target/ramdex-api.jar
java -Xmx1g -jar target/ramdex-api.jar
```

| Configuração | Como definir | Padrão |
|---|---|---|
| Memória máxima (heap) | flag `-Xmx` da JVM (ex.: `-Xmx512m`, `-Xmx2g`) | 1/4 da RAM |
| Porta | variável `PORT` | `8080` |
| Threads que atendem requisições | variável `THREADS` | nº de núcleos |
| Tamanho máximo do corpo | variável `MAX_BODY_MB` | `10` |
| Fila de conexões TCP | variável `BACKLOG` | `1024` |

Exemplo: `PORT=9000 THREADS=4 java -Xmx512m -jar target/ramdex-api.jar`

## Endpoints

Todas as respostas são JSON. Erros vêm como `{"error": "mensagem"}`.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health` | Verificação de saúde |
| GET | `/stats` | Tamanho do índice, heap, GC, memória/CPU do sistema |
| POST | `/documents` | Indexa um documento de texto (ver formatos abaixo). `201` |
| GET | `/documents?offset=0&limit=20` | Lista documentos (sem o conteúdo) |
| GET | `/documents/{id}` | Documento completo |
| DELETE | `/documents/{id}` | Remove o documento do índice |
| GET | `/search?q=...&mode=and\|or&limit=10` | Busca ranqueada por relevância |
| POST | `/generate` | Gera e indexa documentos sintéticos |
| DELETE | `/index?gc=true` | Esvazia o índice (e opcionalmente chama o GC) |

### Enviar documentos (`POST /documents`)

São aceitos apenas textos (`.txt` / `.md`). PDFs e arquivos binários são recusados com `415`.

```bash
# texto puro no corpo (título na query string)
curl -X POST "localhost:8080/documents?title=Minha%20nota" -H "Content-Type: text/plain" \
     --data-binary "O índice invertido fica em memória."

# JSON
curl -X POST localhost:8080/documents -H "Content-Type: application/json" \
     -d '{"title": "Sobre RAM", "content": "Aplicações memory-bound dependem da RAM."}'

# upload de arquivo (multipart/form-data, campo "file"; "title" é opcional)
curl -X POST localhost:8080/documents -F "file=@postman/exemplos/provisionamento.md"

# arquivo como corpo binário
curl -X POST "localhost:8080/documents?title=Indice" -H "Content-Type: text/plain" \
     --data-binary @postman/exemplos/indice-invertido.txt
```

O texto é normalizado: minúsculas, sem acentos e sem pontuação/sintaxe Markdown.

### Buscar (`GET /search`)

```bash
# AND: documentos com todos os termos
curl "localhost:8080/search?q=indice%20memoria" 

# OR: documentos com qualquer termo
curl "localhost:8080/search?q=swap%20cpu&mode=or&limit=5" 
```

```json
{
  "query": "indice memoria", "mode": "and", "terms": ["indice", "memoria"],
  "total": 1, "tookMs": 0.412,
  "results": [{"id": 2, "title": "Sobre RAM", "score": 1.9459, "snippet": "…o índice fica em memória."}]
}
```

O ranking usa TF-IDF: `score(d) = Σ (1 + ln tf(t,d)) · ln(1 + N / df(t))`. `total` é o número de documentos
que satisfazem a consulta, e `results` traz os `limit` mais relevantes.

### Gerador sintético (`POST /generate`)

Cria documentos com pseudo-palavras (`baba`, `beba`, `dacica`…) sorteadas pela lei de Zipf: poucas
palavras muito frequentes e muitas raras, como num texto real. Com a mesma `seed`, o conteúdo gerado é
sempre o mesmo, o que torna os experimentos reproduzíveis.

```bash
curl -X POST localhost:8080/generate -H "Content-Type: application/json" \
     -d '{"docs": 10000, "wordsPerDoc": 200, "vocabSize": 50000, "seed": 42}'
```

Todos os campos são opcionais (os valores acima são os padrões). A resposta traz o tempo gasto, o estado do
índice e `sampleTerms`, com termos frequentes, médios e raros para montar consultas.

**Referência de consumo** (JDK 17, 200 palavras/doc, vocabulário de 50 mil): ~12 MB de heap a cada 1.000
documentos. Com `-Xmx512m`, a geração de 100 mil documentos esgota o heap por volta de 56 mil documentos.
Nesse ponto o GC já consumiu vários segundos, e a API passa a responder `503` (`OutOfMemoryError`).

## Postman

Importe `postman/ramdex.postman_collection.json` (File → Import). A variável de coleção `baseUrl` aponta para
`http://localhost:8080`. Para testar na VM, troque para o IP dela (ex.: `http://192.168.56.10:8080`).

As requisições estão em ordem e podem ser executadas com **Run collection**. As de criação salvam `docId`, e o
gerador salva `frequentTerm`/`rareTerm` para as buscas seguintes. Nos uploads, selecione o arquivo na aba Body
(há exemplos em `postman/exemplos/`).

A mesma coleção roda no terminal com o newman:

```bash
npx newman run postman/ramdex.postman_collection.json --env-var baseUrl=http://localhost:8080
```

## Estrutura

```
src/main/java/br/ramdex/
├── Main.java                  # configuração e inicialização do servidor
├── http/                      # roteador, request/response, JSON e multipart
│   └── handlers/              # um handler por grupo de endpoints
├── index/                     # tokenizer, documento, posting e o índice invertido
├── generator/                 # gerador de documentos sintéticos
└── metrics/                   # métricas da JVM e do sistema (MXBeans)
postman/                       # coleção do Postman e arquivos de exemplo
```

## Testes

```bash
mvn test
```
