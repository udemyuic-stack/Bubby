FROM python:3.11-slim

RUN useradd -m -u 1000 app
WORKDIR /home/app
ENV HF_HOME=/home/app/hf PYTHONUNBUFFERED=1

RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu \
 && pip install --no-cache-dir transformers==4.47.1 ctranslate2==4.5.0 sentencepiece

ARG MODEL=islomov/rubaistt_v2_medium
RUN ct2-transformers-converter --model $MODEL --output_dir /home/app/model --quantization int8 --force \
 && python -c "from transformers import WhisperTokenizerFast; WhisperTokenizerFast.from_pretrained('$MODEL').save_pretrained('/home/app/tok')" \
 && cp /home/app/tok/tokenizer.json /home/app/model/ \
 && python -c "from huggingface_hub import hf_hub_download; import shutil; shutil.copy(hf_hub_download('$MODEL','preprocessor_config.json'),'/home/app/model/preprocessor_config.json')" \
 && rm -rf /home/app/tok /home/app/hf

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
RUN chown -R app:app /home/app
USER app
EXPOSE 7860
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7860"]
