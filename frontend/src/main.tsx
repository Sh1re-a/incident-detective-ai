import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource-variable/instrument-sans/wght.css";
import "@fontsource/ibm-plex-mono/latin-400.css";
import NordlyV2App from "./v2/NordlyV2App";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <NordlyV2App />
  </StrictMode>,
);
