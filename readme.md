You are a senior full-stack engineer and DevOps architect.



Build a production-ready small business cashflow system using:



\- Android app (Kotlin)

\- Node.js backend

\- PostgreSQL

\- Web dashboard

\- Docker (separate stack)

\- Host-level Nginx reverse proxy

\- Domain: flow.cartunez.in



This VPS already has:

\- Docker running other apps

\- Host-level Nginx on ports 80/443

\- We must NOT conflict with existing ports



Use backend port:

127.0.0.1:4100



Only Nginx should expose the app publicly.



=====================================

GOAL

=====================================



Small business with 2 Android phones.



Track:

\- sales

\- expenses

\- purchases

\- profit



Data sources:

\- UPI/bank SMS → auto save

\- WhatsApp notifications → suggest only

\- manual cash entry



System must be:

\- fast

\- offline first

\- lightweight

\- reliable

\- clean UI

\- production ready



=====================================

DOCKER STACK

=====================================



Create separate Docker stack called:



flow-stack



Provide:



docker-compose.yml

Dockerfile

.env example



Services:



1\. flow-postgres

&nbsp;  image: postgres:15

&nbsp;  container\_name: flow-postgres

&nbsp;  separate DB volume



2\. flow-backend

&nbsp;  Node.js Express API

&nbsp;  container\_name: flow-backend

&nbsp;  connects to postgres

&nbsp;  listens on port 4100



Expose backend ONLY to host:



127.0.0.1:4100:4100



Do NOT expose publicly.



=====================================

BACKEND API

=====================================



Tech:

Node.js + Express

PostgreSQL



Tables:

users

businesses

transactions



Transaction fields:

id (uuid)

amount

type

note

date

user\_id

device\_id

created\_at

updated\_at



API routes:



POST /api/auth/simple

POST /api/sync/push

GET /api/sync/pull



Requirements:

\- prevent duplicates

\- lightweight

\- production ready

\- clean structure



=====================================

ANDROID APP

=====================================



Kotlin

MVVM

Room DB

Retrofit



Features:



Home dashboard:

\- today sales

\- today expenses

\- today purchases

\- today profit



Transaction list

Quick add buttons



SMS parser:

auto detect UPI/bank SMS



WhatsApp notification parser:

detect keywords:

paid, received, invoice, transferred



Show confirmation before save.



Sync every 60 sec.



Server URL:

https://flow.cartunez.in/api



=====================================

WEB DASHBOARD

=====================================



Accessible at:

https://flow.cartunez.in/dashboard



Show:

\- daily totals

\- monthly totals

\- transaction list

\- charts



Minimal and fast.



=====================================

NGINX CONFIG

=====================================



Host-level Nginx already running.



Provide config for:



flow.cartunez.in



Reverse proxy:



/api → http://127.0.0.1:4100

/dashboard → backend dashboard



Include:

\- full nginx config

\- HTTPS setup with certbot

\- HTTP → HTTPS redirect



=====================================

DEPLOYMENT COMMANDS

=====================================



Provide full commands:



mkdir flow-stack

cd flow-stack

create docker-compose.yml

docker compose up -d

setup nginx

enable SSL

test API



Also provide:

\- database backup cron

\- container restart policy

\- update commands



=====================================

OUTPUT REQUIRED

=====================================



Give:



\- docker-compose.yml

\- Dockerfile

\- backend code

\- postgres schema

\- nginx config

\- Android project

\- deployment steps

\- PM2 not needed (Docker handles it)



Keep everything clean and production ready.

Do not over engineer.

Focus reliability and clarity.

