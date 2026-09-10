const DAUM_POSTCODE_SCRIPT =
  "https://t1.daumcdn.net/mapjsapi/postcode/prod/postcode.v2.js";
const DAUM_POSTCODE_SCRIPT_ID = "daum-postcode-script";

export type DaumPostcodeData = {
  zonecode: string;
  address: string;
  addressType: "R" | "J";
  userSelectedType: "R" | "J";
  roadAddress: string;
  jibunAddress: string;
  bname: string;
  buildingName: string;
  apartment: "Y" | "N";
};

export type DaumPostcodeInstance = {
  embed: (element: HTMLElement, options?: { autoClose?: boolean }) => void;
};

export type DaumPostcodeConstructor = new (options: {
  oncomplete: (data: DaumPostcodeData) => void;
  onclose?: (state: "FORCE_CLOSE" | "COMPLETE_CLOSE") => void;
  width?: string | number;
  height?: string | number;
}) => DaumPostcodeInstance;

declare global {
  interface Window {
    daum?: {
      Postcode: DaumPostcodeConstructor;
    };
  }
}

let loading: Promise<DaumPostcodeConstructor> | null = null;

export function selectedAddress(data: DaumPostcodeData): string {
  const addr = data.userSelectedType === "R" ? data.roadAddress : data.jibunAddress;
  let extraAddr = "";
  if (data.userSelectedType === "R") {
    if (data.bname !== "" && /[동|로|가]$/g.test(data.bname)) {
      extraAddr += data.bname;
    }
    if (data.buildingName !== "" && data.apartment === "Y") {
      extraAddr += extraAddr !== "" ? `, ${data.buildingName}` : data.buildingName;
    }
    if (extraAddr !== "") {
      extraAddr = ` (${extraAddr})`;
    }
  }
  return addr + extraAddr;
}

export function loadDaumPostcode(): Promise<DaumPostcodeConstructor> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("Daum postcode is browser-only"));
  }
  if (window.daum?.Postcode) {
    return Promise.resolve(window.daum.Postcode);
  }
  if (loading) {
    return loading;
  }

  loading = new Promise((resolve, reject) => {
    const done = () => {
      const Postcode = window.daum?.Postcode;
      if (!Postcode) {
        return false;
      }
      resolve(Postcode);
      return true;
    };

    if (done()) {
      return;
    }

    let script = document.getElementById(DAUM_POSTCODE_SCRIPT_ID) as HTMLScriptElement | null;
    if (!script) {
      script = document.querySelector<HTMLScriptElement>(`script[src="${DAUM_POSTCODE_SCRIPT}"]`);
    }
    if (!script) {
      script = document.createElement("script");
      script.id = DAUM_POSTCODE_SCRIPT_ID;
      script.src = DAUM_POSTCODE_SCRIPT;
      script.async = true;
      document.head.appendChild(script);
    }

    const timeout = window.setTimeout(() => {
      cleanup();
      loading = null;
      reject(new Error("Timed out loading Daum postcode"));
    }, 15000);

    const poll = window.setInterval(() => {
      if (done()) {
        cleanup();
      }
    }, 50);

    const onError = () => {
      cleanup();
      loading = null;
      reject(new Error("Failed to load Daum postcode script"));
    };

    const cleanup = () => {
      window.clearTimeout(timeout);
      window.clearInterval(poll);
      script?.removeEventListener("error", onError);
    };

    script.addEventListener("error", onError, { once: true });
  });

  return loading;
}
