
  import { createRoot } from "react-dom/client";
  import App from "./app/App.tsx";
  import { LoginGate } from "./app/components/LoginGate";
  import { I18nProvider } from "./app/lib/i18n";
  import { consumeUrlToken } from "./app/lib/auth";
  import "./styles/index.css";

  async function bootstrap() {
    await consumeUrlToken();
    createRoot(document.getElementById("root")!).render(
      <I18nProvider>
        <LoginGate>
          <App />
        </LoginGate>
      </I18nProvider>,
    );
  }

  void bootstrap();
  