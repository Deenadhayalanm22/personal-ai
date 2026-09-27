from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math

OUT=Path(__file__).parent
W,H=540,960
FPS=8
DURATION=36
BG='#F5F3EC'; INK='#1E2822'; GREEN='#22684F'; LIGHT='#E8F1DD'; MUTED='#66736A'; LINE='#DCE3D9'; WHITE='#FFFEFA'; WA='#E4F5DB'
FONT='/System/Library/Fonts/SFNS.ttf'

def font(size,bold=False):
    try: return ImageFont.truetype(FONT,size)
    except: return ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc',size)

def rr(d,xy,fill,r=18,outline=None,width=1): d.rounded_rectangle(xy,radius=r,fill=fill,outline=outline,width=width)
def txt(d,xy,s,size=24,color=INK,bold=False,anchor=None): d.text(xy,s,font=font(size,bold),fill=color,anchor=anchor,stroke_width=0,stroke_fill=color)
def center(d,y,s,size=24,color=INK,bold=False): txt(d,(W//2,y),s,size,color,bold,'mm')
def fitlines(d,x,y,lines,size=23,color=INK,leading=1.35,bold=False):
    for i,line in enumerate(lines): txt(d,(x,y+i*size*leading),line,size,color,bold)
def phone(d,kind='app'):
    rr(d,(78,195,462,825),'#172B23',46)
    rr(d,(88,207,452,813),WHITE,37)
    rr(d,(229,213,311,226),'#172B23',10)
    txt(d,(112,229),'9:41',13,INK,True)
    txt(d,(395,229),'●  ▰',13,INK,True)
    if kind=='app':
        txt(d,(112,262),'₹',31,GREEN,True)
        txt(d,(139,268),'Money Stories',23,INK,True)
        d.line((107,311,433,311),fill=LINE,width=2)
    else:
        rr(d,(89,251,451,305),GREEN,0)
        txt(d,(107,267),'‹',30,WHITE)
        txt(d,(145,267),'Money Stories',21,WHITE,True)
        txt(d,(405,269),'⋮',21,WHITE,True)
    rr(d,(101,760,439,801),'#F5F6F0',15)
    for x,s in [(145,'Home'),(223,'Activity'),(306,'Stories'),(387,'You')]:
        txt(d,(x,773),s,11,GREEN if s=='Stories' else MUTED,False,'mt')

def card(d,y,title,sub=None,accent=GREEN,h=82):
    rr(d,(108,y,432,y+h),WHITE,17,LINE,2)
    rr(d,(120,y+13,126,y+h-13),accent,3)
    txt(d,(140,y+16),title,19,INK,True)
    if sub: txt(d,(140,y+47),sub,14,MUTED)

def pill(d,xy,label,color=GREEN,fg=WHITE): rr(d,xy,color,18); txt(d,((xy[0]+xy[2])/2,(xy[1]+xy[3])/2),label,17,fg,True,'mm')
def progress(d,p):
    rr(d,(83,887,457,893),'#D9E2D4',3)
    rr(d,(83,887,83+374*p,893),GREEN,3)

def frame(t):
    im=Image.new('RGB',(W,H),BG); d=ImageDraw.Draw(im)
    scene=min(7,int(t//4.5)); q=(t%4.5)/4.5
    rr(d,(0,0,W,159),LIGHT,0)
    txt(d,(32,30),'MONEY STORIES',15,GREEN,True)
    titles=[('Your money story','starts with a message.'),('Just tell WhatsApp','what you spent.'),('Spoke instead?','Review the words first.'),('Open your portal','with a WhatsApp link.'),('Make it accurate.','Review and edit expenses.'),('Add your money picture','at your own pace.'),('See commitments','in one monthly story.'),('Come back for stories','made from your activity.')]
    a,b=titles[scene]
    txt(d,(32,66),a,32,INK,True); txt(d,(32,105),b,29,GREEN,True)
    if scene in (0,1,2,3): phone(d,'wa')
    else: phone(d,'app')
    if scene==0:
        center(d,386,'Hi, I’m Money Stories',24,GREEN,True)
        center(d,429,'A simpler way to see your money.',17,MUTED)
        rr(d,(115,521,420,620),WA,17)
        fitlines(d,132,541,['Ordered food from Swiggy','for ₹180, paid via UPI'],19,INK,1.55)
        txt(d,(373,590),'✓✓',13,GREEN)
        if q>.45:
            rr(d,(115,642,408,704),WHITE,17)
            txt(d,(134,661),'Ready to record?',19,INK,True)
        center(d,849,'A message is all it takes.',21,GREEN,True)
    elif scene==1:
        rr(d,(115,340,420,438),WA,16)
        fitlines(d,132,359,['Ordered food from Swiggy','for ₹180, paid via UPI'],19,INK,1.55)
        txt(d,(372,409),'✓✓',13,GREEN)
        if q>.2:
            rr(d,(109,465,410,573),WHITE,17)
            fitlines(d,130,483,['Swiggy · ₹180','Food & Dining · UPI'],18,INK,1.55,True)
            pill(d,(257,526,390,558),'Confirm')
        if q>.55:
            rr(d,(115,603,386,661),LIGHT,17)
            txt(d,(135,620),'✓ Expense recorded',19,GREEN,True)
        center(d,849,'Confirm, then it is recorded.',20,GREEN,True)
    elif scene==2:
        rr(d,(144,341,414,407),WA,17)
        txt(d,(164,359),'▶  Voice note  0:06',23,INK,True)
        if q>.2:
            rr(d,(108,439,431,583),WHITE,17)
            txt(d,(128,456),'Review recognized words',18,GREEN,True)
            fitlines(d,128,493,['“Groceries two hundred','and forty rupees”'],20,INK,1.3)
            pill(d,(124,547,294,577),'Confirm words')
        if q>.65:
            rr(d,(108,617,431,682),LIGHT,17)
            txt(d,(127,633),'Next: confirm the expense',18,GREEN,True)
        center(d,849,'You stay in control.',21,GREEN,True)
    elif scene==3:
        rr(d,(110,344,432,450),WHITE,17)
        txt(d,(128,361),'Your sign-in link is ready',19,INK,True)
        txt(d,(128,392),'One-time link sent in WhatsApp',15,MUTED)
        pill(d,(126,412,302,443),'Open portal')
        if q>.5:
            rr(d,(122,495,420,620),LIGHT,17)
            center(d,531,'₹ Money Stories',25,GREEN,True)
            center(d,572,'Good morning',23,INK,True)
        center(d,849,'Tap the link to open your portal.',19,GREEN,True)
    elif scene==4:
        txt(d,(110,338),'Transactions',24,INK,True)
        card(d,386,'Swiggy','Today · Food & Dining · ₹180 · UPI')
        card(d,481,'Groceries','Yesterday · Essentials · ₹240')
        if q>.36:
            rr(d,(105,574,435,732),WHITE,18,LINE,2)
            txt(d,(127,591),'Edit transaction',22,INK,True)
            txt(d,(128,629),'Amount',14,MUTED)
            rr(d,(126,652,410,692),'#F8F9F4',9,LINE)
            txt(d,(140,659),'₹180',19,INK,True)
            pill(d,(235,698,414,729),'Save changes')
        center(d,849,'Check it. Correct it. Trust it.',20,GREEN,True)
    elif scene==5:
        txt(d,(110,331),'Your money',25,INK,True)
        options=[('Loans','Add EMI and payoff details'),('Mutual funds','Track holdings and SIPs'),('Stocks','Add holdings or a plan'),('Commitments','Rent, bills and more')]
        for i,(title,sub) in enumerate(options):
            y=376+i*88
            card(d,y,title,sub, GREEN if q>(i*.19) else '#C8D7C8',h=77)
            if q>(i*.19): txt(d,(399,y+24),'✓',21,GREEN,True)
        center(d,849,'Add only what you want to track.',20,GREEN,True)
    elif scene==6:
        txt(d,(109,330),'Stories',25,INK,True)
        rr(d,(108,374,432,710),LIGHT,21)
        txt(d,(128,395),'MONTHLY COMMITMENT',14,GREEN,True)
        txt(d,(128,432),'Your month, in view.',25,INK,True)
        txt(d,(128,473),'₹38,500 planned',28,GREEN,True)
        d.line((128,522,412,522),fill='#BCD3BA',width=2)
        for i,(label,amt) in enumerate([('Loan EMI','₹12,000'),('Fund SIP','₹6,000'),('Rent & bills','₹20,500')]):
            y=542+i*44
            txt(d,(128,y),label,17,INK)
            txt(d,(409,y),amt,17,GREEN,True,'ra')
        pill(d,(134,668,405,700),'View included commitments')
        center(d,849,'One story. Clear sources.',21,GREEN,True)
    else:
        txt(d,(109,332),'Your stories',25,INK,True)
        card(d,378,'Monthly Commitment','Your upcoming payments, together',h=88)
        if q>.18:
            rr(d,(108,481,432,717),LIGHT,18)
            txt(d,(126,497),'SPENDING STORY',14,GREEN,True)
            fitlines(d,126,526,['Outside food this month'],20,INK,1.2,True)
            txt(d,(126,565),'₹7,000',31,GREEN,True)
            txt(d,(126,610),'That’s 70% of your',18,INK)
            txt(d,(126,637),'₹10,000 monthly rent.',18,INK)
            d.line((126,672,412,672),fill='#BCD3BA',width=2)
            txt(d,(126,685),'Includes Swiggy · ₹180',15,MUTED)
        center(d,849,'Money Stories  ·  Ask me for an invite',21,GREEN,True)
    progress(d,t/DURATION)
    return im

frames=[]
for i in range(DURATION*FPS):
    f=frame(i/FPS)
    frames.append(f.quantize(colors=96,method=2))
frames[0].save(OUT/'money-stories-invite.gif',save_all=True,append_images=frames[1:],duration=round(1000/FPS),loop=0,optimize=True,disposal=2)
frame(35).save(OUT/'poster.png')
print(OUT/'money-stories-invite.gif')
