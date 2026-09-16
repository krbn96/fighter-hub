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
- Swagger UIによるAPI仕様の確認
- 共通エラーハンドリング

### Planned

- ユーザー登録・ログイン
- ユーザープロフィール管理
- 大会情報の閲覧
- チーム作成・編集
- 大会ごとのチーム検索
- 募集中チームへの参加申請
- チームオーナーによる参加申請の承認・拒否
- チームメンバー管理
- 管理者による大会情報の管理

## Tech Stack

### Backend

- Java 21
- Spring Boot 4.0.8
- Spring Web MVC
- Spring Data JPA
- Spring Security
- Hibernate
- Jakarta Validation

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

## API Documentation

実装済みAPIの仕様は、Springdoc OpenAPIによってControllerやDTOの定義から生成しています。

アプリケーション起動後、以下から確認できます。

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

`docs/openapi.yaml` は、今後実装予定のAPIを含む設計資料として管理しています。
実装済みAPIについては、Springdocによって生成されるOpenAPI仕様を基準とします。

## Development Environment

- Java 21
- PostgreSQL 18
- Docker / Docker Compose
- Maven Wrapper

## Setup

### 1. Clone repository

```bash
git clone https://github.com/krbn96/fighter-hub.git
cd fighter-hub
```

### 2. Set database password

データベース接続には環境変数 `DB_PASSWORD` を使用します。

既存の `DB_PASSWORD` を変更したくない場合は、一時的に値を設定して実行することもできます。

macOS / Linux:

```bash
export DB_PASSWORD=your_password
```

Windows PowerShell:

```powershell
$env:DB_PASSWORD="your_password"
```

> `DB_PASSWORD` にはローカル開発環境で使用するPostgreSQLのパスワードを設定してください。
> パスワードをREADMEやGit管理対象のファイルへ直接記載しないでください。

### 3. Start PostgreSQL

Docker Composeを使用してPostgreSQLを起動します。

```bash
docker compose up -d
```

### 4. Start application

macOS / Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw spring-boot:run
```

アプリケーションは以下で起動します。

`http://localhost:8080`

## Testing

JUnit 5、Mockito、MockMvcを使用して、Service層およびController層のテストを実装しています。

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

## Development Status

現在開発中です。

Spring Bootを使用したバックエンドAPIの基盤を構築し、キャラクター情報取得API、共通エラーハンドリング、APIドキュメント、テスト環境まで実装しています。

今後は、認証・ユーザー管理、チーム作成・メンバー募集、参加申請、大会管理などの機能を順次実装する予定です。