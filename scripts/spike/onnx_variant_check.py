import re,sys,numpy as np,onnxruntime as o
from tokenizers import Tokenizer
M='tmp/models/gliner-multi/'
t=Tokenizer.from_file(M+'tokenizer.json')
text="Dear Prof. Martin,\n\nI have been ill this week. Could I have an extension for the CS204 project? The deadline is Friday 6 November. My student number is 20231187."
labels=["person","course","date","deadline","identifier","organization"]
words=re.findall(r'\w+(?:[-_]\w+)*|\S',text)
prompt=[]
for l in labels: prompt+=['<<ENT>>',l]
prompt.append('<<SEP>>')
e=t.encode(prompt+words,is_pretokenized=True,add_special_tokens=True)
pl=len(prompt);wm=[];last=None
for wi in e.word_ids:
    if wi is None or wi<pl: wm.append(0);last=wi;continue
    wm.append(wi-pl+1 if wi!=last else 0);last=wi
n=len(words);W=12
si=np.zeros((1,n*W,2),dtype=np.int64);sm=np.zeros((1,n*W),dtype=bool)
for s in range(n):
    for w in range(W):
        if s+w<n: si[0,s*W+w]=[s,s+w];sm[0,s*W+w]=True
S=o.InferenceSession(M+sys.argv[1],providers=['CPUExecutionProvider'])
import time;st=time.time()
r=S.run(['logits'],dict(input_ids=np.array([e.ids],dtype=np.int64),attention_mask=np.ones((1,len(e.ids)),dtype=np.int64),words_mask=np.array([wm],dtype=np.int64),text_lengths=np.array([[n]],dtype=np.int64),span_idx=si,span_mask=sm))[0]
print(sys.argv[1],'time',round(time.time()-st,2),'max p',float(1/(1+np.exp(-r.max()))))
p=1/(1+np.exp(-r[0]))
for s,w,c in zip(*np.where(p>.5)): print(' ',labels[c],' '.join(words[s:s+w+1]),round(float(p[s,w,c]),2))
