import appPackage from "../../../package.json";

export const APP_VERSION = appPackage.version;
export const APP_ENVIRONMENT_LABEL = import.meta.env.PROD ? "PROD" : "DEV";
