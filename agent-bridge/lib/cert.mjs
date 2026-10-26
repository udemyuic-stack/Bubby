import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { ensureDir } from "./common.mjs";

// Self-signed ECDSA P-256 certificate, made once by the system openssl. Key file is 0600.
export function ensureCert(dir) {
  ensureDir(dir);
  const keyPath = path.join(dir, "key.pem");
  const certPath = path.join(dir, "cert.pem");
  if (!fs.existsSync(keyPath) || !fs.existsSync(certPath)) {
    fs.closeSync(fs.openSync(keyPath, "w", 0o600));
    try {
      execFileSync("openssl", [
        "req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:prime256v1", "-nodes",
        "-keyout", keyPath, "-out", certPath, "-days", "3650", "-subj", "/CN=claude-buddy-bridge",
      ], { stdio: "ignore" });
    } catch (e) {
      fs.rmSync(keyPath, { force: true });
      throw new Error("не удалось создать сертификат: нужен openssl в PATH");
    }
  }
  fs.chmodSync(keyPath, 0o600);
  const key = fs.readFileSync(keyPath);
  const cert = fs.readFileSync(certPath);
  const fp = new crypto.X509Certificate(cert).fingerprint256.replaceAll(":", "").toLowerCase();
  return { key, cert, fp };
}
