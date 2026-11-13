# vacancy-radar

vacancy-radar collects Java job offers from JustJoin.
It uses Claude to extract facts from each offer.
It compares an offer with your resume and gives a score.

## Run on a server with Docker

### Requirements

- A Linux server with Docker.
- Tailscale on the server and on your devices.
- An Anthropic API key.

### Procedure

1. Get the code:

   ```bash
   git clone https://github.com/MaleyDenis/vacancy-radar.git /opt/vacancy-radar
   cd /opt/vacancy-radar
   ```

2. Make the data directory. Give it to the container user (uid 10001):

   ```bash
   mkdir -p data
   chown -R 10001:10001 data
   ```

3. Copy your data from your computer to the server (optional):

   ```bash
   scp data/*.json data/resume.txt root@falcon:/opt/vacancy-radar/data/
   ```

   Then do step 2 again on the server.

4. Write the API key in the file `.env`. Make sure that only root can read this file:

   ```bash
   install -m 600 /dev/null .env
   echo 'ANTHROPIC_API_KEY=<your key>' > .env
   ```

5. Build the image:

   ```bash
   docker build -t vacancy-radar .
   ```

6. Start the container:

   ```bash
   docker run -d --name radar --restart unless-stopped \
     -p "$(tailscale ip -4):8080:8080" \
     -v /opt/vacancy-radar/data:/app/data \
     --env-file .env \
     vacancy-radar
   ```

   **WARNING:** Do not use `-p 8080:8080`. Docker ignores the `ufw` firewall.
   With `-p 8080:8080`, all of the internet can open the app and use your API key.

7. Open `http://falcon:8080` on a device that has Tailscale.

### Update

Get the new code. Then build the image and start the container again:

```bash
cd /opt/vacancy-radar
git pull
docker build -t vacancy-radar .
docker rm -f radar
```

Then do step 6 again. The data stays in `/opt/vacancy-radar/data`.

### Logs

```bash
docker logs -f radar
```
