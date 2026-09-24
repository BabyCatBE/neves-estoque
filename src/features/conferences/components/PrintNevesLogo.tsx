import { useId } from "react";
import nevesLogo from "../../../assets/neves-logo.webp";

type Props = {
  className?: string;
};

export function PrintNevesLogo({ className = "h-14 w-28" }: Props) {
  const rawId = useId().replace(/:/g, "");
  const filterId = `print-neves-luma-${rawId}`;
  const maskId = `print-neves-mask-${rawId}`;

  return (
    <svg
      role="img"
      aria-label="Panificadora Neves"
      viewBox="0 0 600 300"
      className={className}
      preserveAspectRatio="xMidYMid meet"
    >
      <defs>
        <filter
          id={filterId}
          x="0"
          y="0"
          width="100%"
          height="100%"
          colorInterpolationFilters="sRGB"
        >
          <feColorMatrix
            type="matrix"
            values="
              0 0 0 0 1
              0 0 0 0 1
              0 0 0 0 1
              0.2126 0.7152 0.0722 0 0
            "
          />
          <feComponentTransfer>
            <feFuncA type="discrete" tableValues="0 0 0 1 1" />
          </feComponentTransfer>
        </filter>

        <mask id={maskId} maskUnits="userSpaceOnUse" x="0" y="0" width="600" height="300">
          <image
            href={nevesLogo}
            x="0"
            y="0"
            width="600"
            height="300"
            preserveAspectRatio="xMidYMid meet"
            filter={`url(#${filterId})`}
          />
        </mask>
      </defs>

      <rect
        x="0"
        y="0"
        width="600"
        height="300"
        fill="#b91c1c"
        mask={`url(#${maskId})`}
      />
    </svg>
  );
}
