import type { ComponentType } from 'react';
import {
  FileText, Hash, Highlighter, ImagePlus, FileImage, Layers, LayoutGrid, Lock, Minimize2, PenLine,
  RotateCw, ScanText, Scissors, Stamp, Trash2, Unlock, FileOutput, ArrowLeft,
} from 'lucide-react';
import { MergeTool } from './MergeTool';
import { RangeTool } from './RangeTool';
import { OrganizeTool } from './OrganizeTool';
import { ImagesToPdfTool, PdfToImagesTool } from './ImagesTools';
import { WatermarkTool, PageNumbersTool, CompressTool } from './StampTools';
import { SignTool } from './SignTool';
import { ViewerTool } from './ViewerTool';

export type ToolId =
  | 'viewer' | 'merge' | 'split' | 'compress' | 'pdf-images' | 'images-pdf' | 'rotate' | 'reorder'
  | 'delete' | 'extract' | 'watermark' | 'numbers' | 'sign' | 'annotate' | 'protect' | 'unlock' | 'ocr';

export interface Tool {
  id: ToolId; title: string; desc: string; icon: ComponentType<{ className?: string }>;
  Comp?: ComponentType; unavailable?: string; full?: boolean;
}

const Split = () => <RangeTool mode="split" />;
const Extract = () => <RangeTool mode="extract" />;
const Delete = () => <RangeTool mode="delete" />;
const Rotate = () => <OrganizeTool focus="rotate" />;
const Reorder = () => <OrganizeTool focus="reorder" />;

export const TOOLS: Tool[] = [
  { id: 'viewer', title: 'View PDF', desc: 'Open, zoom, rotate and browse pages.', icon: FileText, Comp: ViewerTool, full: true },
  { id: 'merge', title: 'Merge PDF', desc: 'Combine several PDFs into one.', icon: Layers, Comp: MergeTool },
  { id: 'split', title: 'Split PDF', desc: 'Split by ranges or every N pages.', icon: Scissors, Comp: Split },
  { id: 'compress', title: 'Compress PDF', desc: 'Try to reduce file size.', icon: Minimize2, Comp: CompressTool },
  { id: 'pdf-images', title: 'PDF to Images', desc: 'Export pages as PNG or JPG.', icon: FileImage, Comp: PdfToImagesTool },
  { id: 'images-pdf', title: 'Images to PDF', desc: 'Turn photos into a PDF.', icon: ImagePlus, Comp: ImagesToPdfTool },
  { id: 'rotate', title: 'Rotate Pages', desc: 'Rotate single pages or all.', icon: RotateCw, Comp: Rotate },
  { id: 'reorder', title: 'Reorder Pages', desc: 'Rearrange, duplicate or remove pages.', icon: LayoutGrid, Comp: Reorder },
  { id: 'delete', title: 'Delete Pages', desc: 'Remove pages into a new file.', icon: Trash2, Comp: Delete },
  { id: 'extract', title: 'Extract Pages', desc: 'Save selected pages as a new PDF.', icon: FileOutput, Comp: Extract },
  { id: 'watermark', title: 'Add Watermark', desc: 'Stamp text on pages.', icon: Stamp, Comp: WatermarkTool },
  { id: 'numbers', title: 'Add Page Numbers', desc: 'Number pages in your format.', icon: Hash, Comp: PageNumbersTool },
  { id: 'sign', title: 'Sign PDF', desc: 'Place a visual signature.', icon: PenLine, Comp: SignTool },
  { id: 'annotate', title: 'Annotate PDF', desc: 'Notes, highlights, drawing.', icon: Highlighter, unavailable: 'Not available yet in this version.' },
  { id: 'protect', title: 'Protect PDF', desc: 'Password-protect a file.', icon: Lock, unavailable: 'Not available: the PDF library used here cannot encrypt files.' },
  { id: 'unlock', title: 'Unlock PDF', desc: 'Remove a password.', icon: Unlock, unavailable: 'Not available: needs a decryption-capable library.' },
  { id: 'ocr', title: 'OCR / Extract Text', desc: 'Make scans searchable.', icon: ScanText, unavailable: 'Not available: requires an OCR engine or service.' },
];

export const BackIcon = ArrowLeft;
