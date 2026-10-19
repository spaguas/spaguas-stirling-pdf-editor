# Equipes, usuários e permissões Community

O modo `community` combina o editor MIT com uma implementação independente de contas,
equipes e permissões. O backend exclui `app/proprietary` e `app/saas`; o frontend resolve
somente `community → core`. As licenças dos diretórios comerciais permanecem intactas.
Esse modo não inclui SSO, portal comercial, cobrança, armazenamento compartilhado de
PDFs ou outros recursos enterprise. Não existe limite comercial de usuários.

Administradores criam contas, equipes e políticas. Responsáveis por equipe gerenciam
seus membros. Membros acessam somente suas equipes; a visualização da listagem pode
ser restrita aos responsáveis. Para processar PDFs, o usuário precisa pertencer a pelo
menos uma equipe com essa permissão. Administradores podem processar PDFs.

## Preparar a imagem

No servidor, atualize o checkout com os arquivos desta implementação antes dos comandos:

```bash
cd "$HOME/apps/spaguas-stirling-pdf-editor"
mkdir -p "$HOME/.config/stirling-pdf"
umask 077
export COMMUNITY_BOOTSTRAP_PASSWORD_FILE="$HOME/.config/stirling-pdf/community-bootstrap-password"
# Execute somente se o arquivo ainda não existir; não substitua credenciais em uso.
if [ ! -f "$COMMUNITY_BOOTSTRAP_PASSWORD_FILE" ]; then
  openssl rand -base64 24 > "$COMMUNITY_BOOTSTRAP_PASSWORD_FILE"
fi
chmod 600 "$COMMUNITY_BOOTSTRAP_PASSWORD_FILE"
```

O arquivo é montado como secret do Compose e lido pelo Spring Config Tree. A senha não
é um argumento de build. Ela só cria o administrador `admin` em um banco vazio; depois
disso não altera nenhuma conta. Para instalação nova, leia-a localmente no servidor com
`cat "$COMMUNITY_BOOTSTRAP_PASSWORD_FILE"`, entre e troque-a no primeiro acesso.
Não envie essa senha em chamados ou logs.

Defina `STIRLING_DATA_ROOT` com o mesmo valor da instalação atual. O padrão do Compose
é `/mnt/apps/stirling-pdf`. Não aponte para outro diretório durante a migração.

```bash
export STIRLING_DATA_ROOT=/mnt/apps/stirling-pdf
export STIRLING_BUILD_PROXY=http://10.1.6.20:80
export STIRLING_GRADLE_PROXY_FILE="$HOME/.config/stirling-pdf/gradle-proxy.properties"

docker compose --progress plain \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.community-editor-pdf.yml \
  -f docker/compose/docker-compose.build-proxy.yml \
  build stirling-pdf
```

O último arquivo é necessário somente quando o build usa o proxy corporativo. O arquivo
privado do Gradle é o mesmo documentado no [README](README.md); o proxy de saída do build
é independente do Nginx que atende `/editor-pdf`.

## Migrar contas e equipes existentes

Faça a migração antes de iniciar um banco Community pela primeira vez. O importador recusa
um destino com contas e usa transação: qualquer inconsistência desfaz toda a importação.
Ele aceita o esquema H2 com `users`, `authorities`, `teams` e `team_memberships` opcional.
Contas locais BCrypt mantêm a senha e precisam trocá-la no primeiro acesso. Contas sem senha
local compatível, desabilitadas ou com papéis não suportados ficam desabilitadas para revisão;
um administrador pode redefinir suas senhas. É obrigatório haver ao menos um administrador
local ativo na origem. Usuários técnicos internos não são migrados.

Equipes e vínculos são preservados; `LEADER` se torna responsável (`OWNER`). Equipes sem
responsável ativo recebem um administrador importado. Contas sem equipe primária recebem
uma equipe de importação. As equipes importadas permitem processamento e visualização da
listagem; revise essas permissões após a migração. Nomes que colidam após normalização,
referências de equipe inválidas ou esquema incompatível interrompem a operação.
Chaves de API, sessões, SSO, convites, cotas e configurações comerciais não são migrados.
O importador não abre nem altera o banco original: use uma cópia com o serviço parado.

Antes de parar, registre a imagem atual para rollback:

```bash
cid=$(docker compose -f docker/compose/docker-compose.production-editor-pdf.yml ps -q stirling-pdf)
test -n "$cid"
docker image tag "$(docker inspect --format '{{.Image}}' "$cid")" stirling-pdf-before-community:backup

docker compose -f docker/compose/docker-compose.production-editor-pdf.yml stop stirling-pdf

export COMMUNITY_LEGACY_BACKUP_DIR="$HOME/.config/stirling-pdf/legacy-before-community"
mkdir -p "$COMMUNITY_LEGACY_BACKUP_DIR"
chmod 700 "$COMMUNITY_LEGACY_BACKUP_DIR"
# Confirme o nome real antes de copiar; esta é a versão usada pelo projeto.
ls -l "$STIRLING_DATA_ROOT/config/"*.mv.db
cp -p "$STIRLING_DATA_ROOT/config/stirling-pdf-DB-2.3.232.mv.db" "$COMMUNITY_LEGACY_BACKUP_DIR/"
export COMMUNITY_LEGACY_DB_NAME=stirling-pdf-DB-2.3.232
```

Guarde também um backup completo de `config` e dos demais volumes conforme seu procedimento
habitual. Não copie um H2 aberto. Se a origem usa senha de banco diferente do padrão,
configure `community.legacy-password` por outro secret Config Tree; não inclua senha na URL.

```bash
docker compose \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.community-editor-pdf.yml \
  -f docker/compose/docker-compose.community-import.yml \
  up -d --no-build

docker compose \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.community-editor-pdf.yml \
  -f docker/compose/docker-compose.community-import.yml \
  logs --tail=150 stirling-pdf
```

Confirme `Community import complete`, o serviço saudável e as contas/equipes esperadas.
A importação concluída possui marcador persistente e não se repete. Retire o arquivo de
importação dos próximos comandos de inicialização; a cópia da origem deixa de ser montada:

```bash
docker compose \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.community-editor-pdf.yml \
  up -d --no-build
```

Para uma instalação nova, use diretamente esse último comando, sem importação.
Se uma tentativa de importação falhar, corrija a origem de backup e tente novamente com o
override de importação; não inicialize contas manuais no destino entre as tentativas.

## Conferência e reversão

O Nginx existente continua encaminhando somente `/editor-pdf/`; não adicione atalhos em
`/api/` na raiz. Use o [bloco existente](compose/nginx-editor-pdf.conf), com HTTPS no
servidor de produção e os cabeçalhos de encaminhamento já documentados.

```bash
curl -fsS http://127.0.0.1:8080/editor-pdf/api/v1/info/status
curl -fsS http://127.0.0.1:8080/editor-pdf/api/v1/community/session
```

Sem cookie, a sessão deve indicar `authenticated: false`. No navegador, teste login e troca
de senha, criação de usuário e equipe, papel de responsável, acesso negado à administração
por um membro e processamento permitido/bloqueado pela política de equipe. As requisições
devem começar com `/editor-pdf/api/`. Bancos locais anteriores do navegador são preservados,
mas não aparecem automaticamente no novo espaço por usuário; exporte documentos necessários
antes da troca de edição.

O novo banco está em `$STIRLING_DATA_ROOT/config/community/identity.mv.db`. Preserve esse
diretório e faça backups com o serviço parado. Ele contém hashes, vínculos e chave interna;
restrinja acesso ao diretório e às cópias. Não use mais de uma instância contra esse H2.

Para voltar à edição anterior, pare Community e inicie a imagem preservada com o Compose
original e um override de imagem (sem os arquivos Community/importação):

```bash
docker compose \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f docker/compose/docker-compose.community-editor-pdf.yml stop stirling-pdf

printf '%s\n' 'services:' '  stirling-pdf:' '    image: stirling-pdf-before-community:backup' \
  > "$HOME/.config/stirling-pdf/rollback-community.yml"
docker compose \
  -f docker/compose/docker-compose.production-editor-pdf.yml \
  -f "$HOME/.config/stirling-pdf/rollback-community.yml" up -d --no-build
```

As alterações feitas em contas Community não voltam para o banco antigo. O rollback também
restaura as condições e requisitos da edição anterior. A documentação prepara os comandos;
a implantação no servidor deve ser executada pelo operador.
