import concurrent.futures,subprocess,json,time,tempfile,pathlib,hashlib,threading
import argparse,datetime
parser=argparse.ArgumentParser(description='仅请求已审核的公开 GET 接口，不保存响应正文')
parser.add_argument('--output',required=True)
args=parser.parse_args()
out=pathlib.Path(args.output); out.mkdir(parents=True,exist_ok=True); lock=threading.Lock()
paths=['/api/bootstrap','/api/videos/getAllVideos?page=0&size=10','/api/music/page?page=0&size=10','/api/quotes/page?page=0&size=10','/api/articles/grouped-by-type','/api/white-noises','/api/pure-musics','/api/breathe/getAllBreathe','/api/meditate-videos/getAllMeditateVideos?page=0&size=10','/api/video-scenarios/getAllVideoScenarios?page=0&size=10','/api/sister-motivation-videos/list','/api/posts/getAllPosts?page=0&size=5']
paths += ['/api/videos/cn/getAllVideos?page=0&size=10','/api/music/cn/page?page=0&size=10','/api/music/getAllMusic','/api/music/cn/getAllMusic','/api/articles/all?page=0&size=10','/api/articles/cn/all?page=0&size=10','/api/articles/cn/grouped-by-type','/api/channels/all','/api/quotes/getAllQuotes','/api/meditate-videos/cn/getAllMeditateVideos?page=0&size=10','/api/video-scenarios/cn/getAllVideoScenarios?page=0&size=10','/api/sister-motivation-videos/getAllVideos?page=0&size=10','/api/posts/getAllPosts?page=1&size=5','/api/posts/getAllPosts?page=0&size=5&sortBy=created_at&sortDir=asc']
def probe(host):
 for repeat in range(3):
  for path in paths:
   with tempfile.TemporaryDirectory(prefix='quittr-public-') as tmp:
    body=pathlib.Path(tmp)/'body'; headers=pathlib.Path(tmp)/'headers'
    cmd=['curl','--silent','--show-error','--compressed','--max-time','20','--connect-timeout','8','-D',str(headers),'-o',str(body),'-w','%{http_code} %{time_starttransfer} %{time_total} %{size_download} %{remote_ip}',host+path]
    proc=subprocess.run(cmd,capture_output=True,text=True); row={'host':host,'path':path,'repeat':repeat+1,'curl_exit':proc.returncode,'checked_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat()}; vals=proc.stdout.split()
    if len(vals)>=4:row.update(status=int(vals[0]),ttfb_ms=round(float(vals[1])*1000,1),total_ms=round(float(vals[2])*1000,1),wire_bytes=int(vals[3]),remote_ip=vals[4] if len(vals)>4 else None)
    if body.exists():
     raw=body.read_bytes();row['decoded_bytes']=len(raw)
     try:
      data=json.loads(raw);row['json']=True;row['success']=data.get('success') if isinstance(data,dict) else None
      payload=data.get('data') if isinstance(data,dict) else data
      row['data_type']=type(payload).__name__
      if isinstance(payload,list):row['items']=len(payload)
      if isinstance(payload,dict):
       row['data_keys']=list(payload.keys())[:15]
       if isinstance(payload.get('content'),list):row['items']=len(payload['content'])
     except Exception:row['json']=False
    if headers.exists():
     wanted={'content-type','content-encoding','cache-control','etag','vary','server','cf-cache-status','age'}
     row['headers']={k.lower().strip():v.strip() for line in headers.read_text(errors='replace').splitlines() if ':' in line for k,v in [line.split(':',1)] if k.lower().strip() in wanted}
    if proc.returncode:row['error']=proc.stderr.strip()[:250]
    with lock:
     with (out/'public-probes.jsonl').open('a') as f:f.write(json.dumps(row,ensure_ascii=False)+'\n')
    time.sleep(.25)
(out/'public-probes.jsonl').write_text('')
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(probe,['https://kejiapi.cn','https://globalkejiapi.asia','https://globalkejiapi.top']))
print('Completed public GET probes; response bodies discarded.')
