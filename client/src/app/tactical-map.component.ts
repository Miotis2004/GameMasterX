import { Component, signal, computed, ElementRef, ViewChild, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

interface GridPos {
  x: number;
  y: number;
}

interface ParticipantMock {
  id: string;
  name: string;
  pos: GridPos;
  movementSpeed: number;
}

@Component({
  selector: 'app-tactical-map',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tactical-map.component.html',
  styleUrl: './tactical-map.component.css'
})
export class TacticalMapComponent {
  @ViewChild('svgRef', { static: false }) svgRef!: ElementRef<SVGSVGElement>;

  // Grid config
  readonly gridSize = 30;
  readonly cellSize = 32;

  // View state
  readonly zoom = signal(1);
  readonly panX = signal(0);
  readonly panY = signal(0);
  readonly isPanning = signal(false);
  private lastPanX = 0;
  private lastPanY = 0;

  // Mock participants
  readonly participants = signal<ParticipantMock[]>([
    { id: 'p1', name: 'Aragorn', pos: { x: 5, y: 5 }, movementSpeed: 6 },
    { id: 'p2', name: 'Gandalf', pos: { x: 10, y: 8 }, movementSpeed: 4 },
    { id: 'p3', name: 'Legolas', pos: { x: 12, y: 12 }, movementSpeed: 8 },
  ]);

  readonly selectedId = signal<string | null>(null);
  readonly hoverPos = signal<GridPos | null>(null);
  readonly mousePos = signal<{ x: number; y: number } | null>(null);

  // Computed view transform
  readonly viewTransform = computed(() => {
    const z = this.zoom();
    const x = this.panX();
    const y = this.panY();
    return `translate(${x}px, ${y}px) scale(${z})`;
  });

  readonly viewBox = computed(() => `0 0 ${this.gridSize * this.cellSize} ${this.gridSize * this.cellSize}`);

  readonly gridNumbers = computed(() => Array.from({ length: this.gridSize }, (_, i) => i));

  // Selection
  selectParticipant(id: string) {
    this.selectedId.set(this.selectedId() === id ? null : id);
  }

  // Zoom
  @HostListener('wheel', ['$event'])
  onWheel(event: WheelEvent) {
    event.preventDefault();
    const delta = event.deltaY > 0 ? 0.9 : 1.1;
    const newZoom = Math.min(3, Math.max(0.3, this.zoom() * delta));
    this.zoom.set(newZoom);
  }

  // Pan handlers
  onMouseDown(event: MouseEvent) {
    this.isPanning.set(true);
    this.lastPanX = event.clientX;
    this.lastPanY = event.clientY;
  }

  onMouseUp() {
    this.isPanning.set(false);
  }

  onMouseMove(event: MouseEvent) {
    if (this.isPanning()) {
      const dx = event.clientX - this.lastPanX;
      const dy = event.clientY - this.lastPanY;
      this.panX.set(this.panX() + dx);
      this.panY.set(this.panY() + dy);
      this.lastPanX = event.clientX;
      this.lastPanY = event.clientY;
    }
    // Update mouse position for coordinates
    this.updateMousePos(event);
  }

  private updateMousePos(event: MouseEvent) {
    const svg = this.svgRef?.nativeElement;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    this.mousePos.set({ x, y });
    // Convert to grid
    const gridX = Math.floor((x / this.zoom() - this.panX()) / this.cellSize);
    const gridY = Math.floor((y / this.zoom() - this.panY()) / this.cellSize);
    this.hoverPos.set({ x: gridX, y: gridY });
  }

  // Movement preview
  movementSquares = computed(() => {
    const selected = this.selectedId();
    if (!selected) return [];
    const participant = this.participants().find(p => p.id === selected);
    if (!participant) return [];
    const speed = participant.movementSpeed;
    const squares: GridPos[] = [];
    for (let dx = -speed; dx <= speed; dx++) {
      for (let dy = -speed; dy <= speed; dy++) {
        const dist = Math.abs(dx) + Math.abs(dy);
        if (dist <= speed) {
          squares.push({ x: participant.pos.x + dx, y: participant.pos.y + dy });
        }
      }
    }
    return squares;
  });

  // Visibility
  visibilityRadius = 6;

  isVisible(pos: GridPos): boolean {
    const selected = this.selectedId();
    if (!selected) return true;
    const participant = this.participants().find(p => p.id === selected);
    if (!participant) return true;
    const dx = pos.x - participant.pos.x;
    const dy = pos.y - participant.pos.y;
    const dist = Math.sqrt(dx * dx + dy * dy);
    return dist <= this.visibilityRadius;
  }

  // Helpers for template
  gridCells() {
    const cells: GridPos[] = [];
    for (let y = 0; y < this.gridSize; y++) {
      for (let x = 0; x < this.gridSize; x++) {
        cells.push({ x, y });
      }
    }
    return cells;
  }

  participantAt(pos: GridPos) {
    return this.participants().find(p => p.pos.x === pos.x && p.pos.y === pos.y);
  }

  isReachable(pos: GridPos): boolean {
    const squares = this.movementSquares();
    return squares.some(s => s.x === pos.x && s.y === pos.y);
  }
}
