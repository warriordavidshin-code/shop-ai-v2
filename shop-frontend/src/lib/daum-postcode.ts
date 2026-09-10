const DAUM_POSTCODE_SCRIPT =
  "https://t1.daumcdn.net/mapjsapi/postcode/prod/postcode.v2.js";

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

export type DaumPostcodeConstructor = new (options: {
  oncomplete: (data: DaumPostcodeData) => void;
}) => { open: () => void };

declare global {
  interface Window {
    daum?: {
      Postcode: DaumPostcodeConstructor;
    };
  }
}

let loading: Promise<DaumPostcodeConstructor> | null = null;

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
    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${DAUM_POSTCODE_SCRIPT}"]`,
    );
    const script = existing ?? document.createElement("script");
    const onLoad = () => {
      if (window.daum?.Postcode) {
        resolve(window.daum.Postcode);
      } else {
        loading = null;
        reject(new Error("Daum Postcode API was not initialized"));
      }
    };
    const onError = () => {
      loading = null;
      reject(new Error("Failed to load Daum postcode script"));
    };
    script.addEventListener("load", onLoad, { once: true });
    script.addEventListener("error", onError, { once: true });
    if (!existing) {
      script.src = DAUM_POSTCODE_SCRIPT;
      script.async = true;
      document.head.appendChild(script);
    } else if (window.daum?.Postcode) {
      onLoad();
    }
  });
  return loading;
}
