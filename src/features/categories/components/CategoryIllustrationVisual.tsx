import { CategoryLibraryIllustration } from "./CategoryIllustrationLibrary";

type Props = {
  source: "library" | "upload" | null;
  illustrationKey: string | null;
  illustrationUrl?: string | null;
  positionX?: number | null;
  positionY?: number | null;
  monochrome?: boolean;
  className?: string;
};

export function CategoryIllustrationVisual({
  source,
  illustrationKey,
  illustrationUrl,
  positionX = 50,
  positionY = 50,
  monochrome = false,
  className = "h-12 w-12"
}: Props) {
  if (source === "library" && illustrationKey) {
    return (
      <div
        className={`flex items-center justify-center overflow-hidden rounded-xl bg-red-50 text-red-700 ${className}`}
      >
        <CategoryLibraryIllustration illustrationKey={illustrationKey} className="h-8 w-8" />
      </div>
    );
  }

  if (source === "upload" && illustrationUrl) {
    return (
      <div
        className={`relative isolate overflow-hidden rounded-xl bg-zinc-100 ${className}`}
      >
        <img
          src={illustrationUrl}
          alt=""
          aria-hidden="true"
          className="h-full w-full object-cover"
          style={{
            objectPosition: `${positionX ?? 50}% ${positionY ?? 50}%`,
            filter: monochrome ? "grayscale(1) contrast(1.1)" : undefined
          }}
        />
        {monochrome ? (
          <span
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 bg-red-700"
            style={{ mixBlendMode: "color" }}
          />
        ) : null}
      </div>
    );
  }

  return (
    <div
      className={`flex items-center justify-center overflow-hidden rounded-xl bg-red-50 text-red-700 ${className}`}
    >
      <GenericCategoryIcon />
    </div>
  );
}

function GenericCategoryIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="h-5 w-5" fill="none">
      <path
        d="M4.5 6.5A2.5 2.5 0 0 1 7 4h3l1.4 2H17a2.5 2.5 0 0 1 2.5 2.5v8A2.5 2.5 0 0 1 17 19H7a2.5 2.5 0 0 1-2.5-2.5v-10Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
    </svg>
  );
}
