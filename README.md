# vacancy-radar

Collects Java job offers from [JustJoin](https://justjoin.it), extracts the facts from each offer
with Claude, and scores offers against your resume.

## What you need

- Docker
- An Anthropic API key (Claude)

## Run

```bash
git clone https://github.com/MaleyDenis/vacancy-radar.git
cd vacancy-radar
docker build -t vacancy-radar .

docker run -d --name radar --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  -v "$PWD/data:/app/data" \
  -e ANTHROPIC_API_KEY=sk-ant-... \
  vacancy-radar
```

Open http://localhost:8080, paste your resume on the **Резюме** tab and press **Сканировать**.

All data is plain JSON in `./data` and survives restarts and rebuilds.
