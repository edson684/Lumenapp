# Lumen - APK (Capacitor)

O app roda sozinho no celular: as requisições a listas e canais são feitas pelo próprio Android (sem server.js e sem proxy).

## Gerar o APK (uma vez)
1. Crie uma conta em github.com e um repositório NOVO e PRIVADO (ex.: lumen-iptv).
2. Envie TODO o conteúdo desta pasta para o repositório (inclusive a pasta oculta .github e a pasta ci).
3. Abra a aba Actions do repositório. O fluxo "Gerar APK" começa sozinho (leva uns 5 a 10 minutos). Se não começar, escolha "Gerar APK" e clique em Run workflow.
4. Quando terminar, abra a aba Releases (lado direito da página inicial do repositório) e baixe o Lumen.apk. Instale no celular (permita "instalar apps desconhecidos").

## Atualizar o app
1. Troque o arquivo www/index.html no repositório (ou qualquer outro arquivo).
2. O GitHub gera um APK novo, com número de versão maior, e publica em Releases.
3. Baixe e instale POR CIMA do app antigo. Seus dados (fontes, favoritos, recentes) continuam.

A chave de assinatura fica em ci/debug.keystore. Não apague nem troque esse arquivo: é ela que permite instalar por cima. Mantenha o repositório privado.

## Aviso de atualização dentro do app
Ao abrir, o app consulta as versões publicadas em Releases do repositório edson684/lumenapp (precisa ser PÚBLICO) e mostra um aviso quando existe APK mais novo. Também há o botão "Verificar atualização" em Fontes.

## Arquivos nativos (pasta native/)
LumenPlugin.java e MainActivity.java são copiados para o projeto Android durante a compilação. Eles cuidam da tela cheia sem barras do sistema e dos downloads pelo gerenciador do Android (com notificação, em segundo plano).

## Atualização automática
A interface (www/) é atualizada sozinha: a cada push o GitHub publica o APK e um bundle.zip. O app baixa o bundle ao abrir e aplica na próxima abertura. Se a interface nova tiver erro, o app volta sozinho para a anterior.
Quando a mudança mexer no código nativo (pasta native/, package.json ou capacitor.config.json), escreva [apk] na mensagem do commit. Aí o app mostra o aviso para baixar o APK novo.
