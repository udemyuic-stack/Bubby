---
title: Bubby Uzbek STT
sdk: docker
app_port: 7860
---

Uzbek speech to text for Bubby: islomov/rubaistt_v2_medium converted to CTranslate2 int8, served with FastAPI.

POST /transcribe with the audio bytes and header `Authorization: Bearer <STT_TOKEN>`. Reply: `{"text": "..."}`.
Set `STT_TOKEN` as a Space secret.
