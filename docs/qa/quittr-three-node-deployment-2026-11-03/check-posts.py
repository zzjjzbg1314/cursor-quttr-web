import concurrent.futures,json,subprocess,tempfile,pathlib
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--output',required=True)
out=pathlib.Path(parser.parse_args().output)
out.mkdir(parents=True,exist_ok=True)
def check(host):
 rows=[]
 for page,direction in [(0,'desc'),(1,'desc'),(0,'asc')]:
  path=f'/api/posts/getAllPosts?page={page}&size=5&sortBy=created_at&sortDir={direction}'
  proc=subprocess.run(['curl','--silent','--show-error','--compressed','--max-time','20',host+path],capture_output=True)
  row={'host':host,'path':path,'curl_exit':proc.returncode}
  try:
   data=json.loads(proc.stdout);payload=data['data'];posts=payload['content']
   row['checks']={'business_success':data.get('success') is True,'page_number':payload['number']==page,'page_size':payload['size']==5,'page_length':len(posts)<=5 and payload['numberOfElements']==len(posts),'total_consistency':payload['totalElements']>=len(posts),'unique_ids':len({p['postId'] for p in posts})==len(posts),'statistics_nonnegative':all(isinstance(p.get('upvotes'),int) and p['upvotes']>=0 and isinstance(p.get('commentCount'),int) and p['commentCount']>=0 for p in posts),'not_deleted':all(p.get('isDeleted') is False for p in posts)}
   times=[p['createdAt'] for p in posts];row['checks']['sort_order']=times==sorted(times,reverse=direction=='desc')
   row['items']=len(posts);row['total']=payload['totalElements'];row['pass']=all(row['checks'].values())
  except Exception as e:row['pass']=False;row['error_type']=type(e).__name__
  rows.append(row)
 return rows
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 rows=[r for batch in pool.map(check,['https://kejiapi.cn','https://globalkejiapi.asia','https://globalkejiapi.top']) for r in batch]
(out/'post-validation.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(rows,ensure_ascii=False))
