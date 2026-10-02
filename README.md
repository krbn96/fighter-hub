# FIGHTER HUB
FIGHTER HUBは、格闘ゲームのチーム戦大会に参加するプレイヤー向けの、チーム作成・メンバー募集を支援するWebアプリケーションです。

## Overview
格闘ゲームの3on3や5on5などのチーム戦大会では、
「大会に参加したいが一緒に出場するメンバーが見つからない」
「チームを作ったが、あと1人メンバーを募集したい」
といったケースがあります。

FIGHTER HUBでは、大会ごとにチームを作成してメンバーを募集したり、
参加したいチームを探して応募したりできる仕組みを提供することで、
チーム戦大会に参加するプレイヤー同士のマッチングを支援します。

## Features
予定している主要機能

### Implemented

- キャラクター一覧取得
- キャラクター詳細取得
- ユーザー登録
- ログイン（JWT発行）
- JWT Bearer認証（Spring Security OAuth2 Resource Server, Stateless）
- 公開ユーザープロフィール取得
- 自分のプロフィール取得・更新（JWT認証必須）
- ソフトデリート済みユーザーの認証・取得対象外化
- チーム作成（作成者をownerとして自動登録、ownerをチームメンバーとして自動登録）
- 同一大会内でのチーム重複所属防止
- チーム一覧取得
- チーム詳細取得
- 自分が所属しているチーム一覧取得
- チーム編集（ownerのみ実行可能）
- チームメンバー一覧取得
- チームメンバー削除（ownerのみ実行可能、owner自身は削除不可）
- チームへの参加申請
- Team ownerによる参加申請一覧取得（PENDING/APPROVED/REJECTEDすべて確認可能）
- 自分が送った参加申請履歴の取得
- Team ownerによる参加申請の承認・拒否
- 参加申請承認時のチームメンバー自動追加（承認とメンバー追加は同一トランザクション）
- 同一大会内で同時に保持できるPENDING申請は1ユーザーにつき1件まで
- 同一大会内では1チームにのみ所属可能（重複所属防止）
- チーム定員（Tournament.teamSize）を超える承認の防止
- Pessimistic Lockによる同時申請・同時承認時の競合対策
- 大会一覧取得・大会詳細取得
- ADMINユーザーによる大会作成・更新・論理削除（ADMIN判定はDB上のUser.roleを基準とする認可）
- 大会ごとのチーム一覧取得
- Tournamentに募集締切日時（recruitmentDeadline）を設定（recruitmentDeadline < startAt）
- 募集締切後の新規チーム作成・新規参加申請の禁止（締切後も既存PENDING申請の承認・拒否は可能）
- 募集状態（RECRUITING/CLOSED）はDBへ保存せず、recruitmentDeadlineと現在時刻から導出
- Swagger UIによるAPI仕様の確認（Bearer認証対応）
- 共通エラーハンドリング

## Implemented Frontend Features

Week 7でVue 3 + TypeScriptによるフロントエンドを実装しました。

- ログイン
- マイページ
- 大会一覧・大会詳細
- 大会ごとの募集チーム一覧
- チーム詳細
- チーム作成
- ownerによるチーム編集
- 自分の所属チーム一覧
- チームへの参加申請
- 自分の参加申請一覧
- ownerによる参加申請一覧・承認・拒否

## Tech Stack

### Backend

- Java 21
- Spring Boot 4.0.8
- Spring Web MVC
- Spring Data JPA
- Spring Security
- Hibernate
- Jakarta Validation

### Frontend

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Vitest

### Database

- PostgreSQL 18

### API Documentation

- OpenAPI
- Springdoc OpenAPI
- Swagger UI

### Development / Testing

- Maven
- JUnit 5
- Mockito
- MockMvc
- Docker / Docker Compose
- Git / GitHub

## Architecture

バックエンドは、Controller・Service・Repositoryの各レイヤーに責務を分離しています。

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
PostgreSQL
```

- **Controller**: HTTPリクエストの受付とレスポンスの返却
- **Service**: ビジネスロジックとトランザクション管理
- **Repository**: Spring Data JPAを利用したデータアクセス
- **Entity**: データベースのテーブル構造をJavaオブジェクトとして表現
- **DTO**: APIの入出力モデルとしてEntityとAPIを分離
- **GlobalExceptionHandler**: APIで発生した例外を共通のエラーレスポンスへ変換

## Frontend Architecture

`frontend/`配下はVue 3 + TypeScriptで構成しています。

- **views**: 画面単位のVueコンポーネント。APIの呼び出しと画面表示を行う
- **router**: Vue Routerによるルーティング定義。`meta.requiresAuth`で認証が必要なページを制御する
- **stores**: Piniaによる状態管理。現状は認証状態を扱う`auth` storeのみ
- **api**: Axiosを使ったバックエンドAPI呼び出し層。エンドポイントごとに薄い関数として実装している
- **types**: バックエンドの各DTOに対応するTypeScript型定義

## Authentication

BackendはJWT Bearer認証（Spring Security OAuth2 Resource Server, Stateless）でAPIを保護しています。

Frontendでは以下の仕組みで認証状態を扱います。

- JWTをlocalStorageに保存
- Axios interceptor（`api/client.ts`）でリクエストへ`Authorization: Bearer <token>`を自動付与
- Pinia auth store（`stores/auth.ts`）で認証状態（token・ログインユーザー情報）を管理
- Vue Routerの`meta.requiresAuth`で保護ページへのアクセスを制御

## API Documentation

API仕様はJava実装（Controller / DTO / Validation等）をSource of Truthとします。実装済みAPIの仕様は、Springdoc OpenAPIによってControllerやDTOの定義から生成しています。

### 実装済みAPI

| Method | Path | 認証 |
|---|---|---|
| GET | /api/characters | 不要 |
| GET | /api/characters/{id} | 不要 |
| POST | /api/users | 不要 |
| GET | /api/users/{id} | 不要 |
| POST | /api/auth/login | 不要 |
| GET | /api/users/me | JWT必須 |
| PATCH | /api/users/me | JWT必須 |
| POST | /api/teams | JWT必須 |
| GET | /api/teams | 不要 |
| GET | /api/teams/{id} | 不要 |
| GET | /api/teams/my | JWT必須 |
| PATCH | /api/teams/{id} | JWT必須 |
| GET | /api/teams/{id}/members | 不要 |
| DELETE | /api/teams/{teamId}/members/{userId} | JWT必須 |
| POST | /api/teams/{teamId}/applications | JWT必須 |
| GET | /api/teams/{teamId}/applications | JWT必須 |
| GET | /api/applications/me | JWT必須 |
| PATCH | /api/teams/{teamId}/applications/{applicationId}/approve | JWT必須 |
| PATCH | /api/teams/{teamId}/applications/{applicationId}/reject | JWT必須 |
| GET | /api/tournaments | 不要 |
| GET | /api/tournaments/{id} | 不要 |
| POST | /api/tournaments | JWT必須 |
| PATCH | /api/tournaments/{id} | JWT必須 |
| DELETE | /api/tournaments/{id} | JWT必須 |
| GET | /api/tournaments/{tournamentId}/teams | 不要 |

詳細な仕様は、アプリケーション起動後に以下から確認できます。

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

これらはJava実装から自動生成されるAPI仕様・確認手段です。

`docs/openapi.yaml` は、現在実装済みのAPIを記録するリポジトリ内のAPI仕様書として管理しています。未実装・将来予定のAPIは記載せず、API実装・変更が完了するたびにJava実装へ同期します。

## Development Environment

- Java 21
- PostgreSQL 18
- Docker / Docker Compose
- Maven Wrapper

## Setup

FIGHTER HUB全体（Frontend / Backend / PostgreSQL）をDocker Composeでまとめて起動する方法を推奨します。Backend / Frontendを個別にホスト上で起動する従来の開発方法も引き続き利用できます。

### 1. Clone repository

```bash
git clone https://github.com/krbn96/fighter-hub.git
cd fighter-hub
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

`.env` を編集し、以下を設定してください（`.env` はGit管理対象外です。実値をREADMEやGit管理対象のファイルへ直接記載しないでください）。

- `DB_PASSWORD`: PostgreSQLの接続パスワード（任意の文字列）
- `JWT_SECRET`: JWT署名用secret。Base64エンコードされた32byte（256bit）以上の値が必要です（生成例: `openssl rand -base64 32`）

### 3. Start with Docker Compose（推奨）

```bash
docker compose up --build
```

起動後、ブラウザから `http://localhost` へアクセスしてください。Frontend（Nginx）が `/api` 宛のリクエストを同一オリジンでBackendへreverse proxyするため、CORS設定は不要です。

### ローカル開発時の個別起動（Docker無し）

Backend / Frontendをホスト上で個別に起動して開発する場合は、環境変数（`DB_PASSWORD` / `JWT_SECRET`）を設定した上で以下を実行します。Docker版PostgreSQLはhostへportを公開していないため、この方法を使う場合は別途ローカルにPostgreSQLを用意してください。

Backend:

```bash
./mvnw spring-boot:run
```

Windows PowerShellの場合は `.\mvnw spring-boot:run` を使用してください。`http://localhost:8080` で起動します。

Frontend:

```bash
cd frontend
npm install
npm run dev
```

開発時は、Vite dev serverの設定（`frontend/vite.config.ts`）により `/api` 宛のリクエストをSpring Boot（`http://localhost:8080`）へproxyします。

## Testing

JUnit 5、Mockito、MockMvcを使用して、Service層およびController層のテストを実装しています。
参加申請の承認・定員チェックなど排他制御が関わる処理については、実PostgreSQLを使った並行実行のIntegration Testでも整合性を確認しています。

### Run tests

macOS / Linux:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw test
```

クリーンビルドからすべてのテストを実行する場合:

macOS / Linux:

```bash
./mvnw clean test
```

Windows:

```powershell
.\mvnw clean test
```

## Frontend Test / Quality Check

`frontend`ディレクトリで以下を実行します。

```bash
npm run type-check
npm run lint
npm run test:unit -- --run
```

## Development Status

現在開発中です。

Spring Bootを使用したバックエンドAPIの基盤を構築し、キャラクター情報取得API、ユーザー登録、JWT認証によるログイン、自分のプロフィール取得・更新、チーム作成・編集・メンバー一覧取得・メンバー削除、チームへの参加申請と承認・拒否（承認時のメンバー自動追加、定員チェック、Pessimistic Lockによる同時実行対策を含む）、大会の一覧・詳細取得およびADMINユーザーによる大会作成・更新・論理削除、大会ごとのチーム一覧取得、共通エラーハンドリング、APIドキュメント、テスト環境まで実装しています。

Week 7では、Vue 3 + TypeScriptによるフロントエンドを実装しました。ログイン、マイページ、大会・チームの閲覧、チーム作成・編集、参加申請とownerによる承認・拒否まで、主要なユーザー操作を画面から一通り行える状態になっています。

Week 8では、Docker Composeによるアプリケーション全体（Frontend/Backend/PostgreSQL）の起動に対応しました。AWS等へのデプロイはまだ行っていません。