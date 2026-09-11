import { Component, signal, computed, ElementRef, ViewChild, HostListener, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { TacticalMapFacadeService } from './tactical-map-facade.service';
import { TacticalMapDto, TokenDto } from './tactical-map-api.service';

interface GridPos {
  x: number;
  y: number;
}

@Component({
  selector: 'app-tactical-map',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './tactical-map.component.html',
  styleUrl: './tactical-map.component.css'
})
export class TacticalMapComponent implements OnInit {
  @ViewChild('svgRef', { static: false }) svgRef!: ElementRef<SVGSVGElement>;

  private readonly facade = inject(TacticalMapFacadeService);
  private readonly route = inject(ActivatedRoute);

  private readonly mapIdSignal = signal<string | null>(null);
  readonly mapSignal = signal<TacticalMapDto | null>(null);

  readonly zoom = signal(1);
  readonly panX = signal(0);
  readonly panY = signal(0);
  readonly isPanning = signal(false);
  private lastPanX = 0;
  private lastPanY = 0;

  readonly selectedId = signal<string | null>(null);
  readonly hoverPos = signal<GridPos | null>(null);
  readonly mousePos = signal<{ x: number; y: number } | null>(null);
  readonly reachableCells = signal<GridPos[]>([]);
  readonly visibilityRadius = 6;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.mapIdSignal.set(id);
      this.facade.loadMap(id);
      this.facade.map$.subscribe(map => this.mapSignal.set(map));
    }
  }

  get map(): TacticalMapDto | null {
    return this.mapSignal();
  }

  get gridDefinition() {
    return this.map?.gridDefinition;
  }

  get cellSize(): number {
    return this.gridDefinition?.cellSize ?? 32;
  }

  get gridWidth(): number {
    return this.gridDefinition?.width ?? 0;
  }

  get gridHeight(): number {
    return this.gridDefinition?.height ?? 0;
  }

  get tokens(): TokenDto[] {
    return this.map?.tokens ?? [];
  }

  readonly viewTransform = computed(() => {
    const z = this.zoom();
    const x = this.panX();
    const y = this.panY();
    return `translate(${x}px, ${y}px) scale(${z})`;
  });

  readonly viewBox = computed(() => `0 0 ${this.gridWidth * this.cellSize} ${this.gridHeight * this.cellSize}`);

  readonly gridNumbers = computed(() => Array.from({ length: Math.max(this.gridWidth, this.gridHeight) }, (_, i) => i));

  selectToken(id: string) {
    const newId = this.selectedId() === id ? null : id;
    this.selectedId.set(newId);
    if (newId && this.mapIdSignal() && this.map) {
      const range = 5;
      this.facade.getRangeOverlay(this.mapIdSignal()!, newId, range).subscribe(cells => {
        const positions: GridPos[] = cells.map(c => ({ x: c.x, y: c.y }));
        this.reachableCells.set(positions);
      });
    } else {
      this.reachableCells.set([]);
    }
  }

  @HostListener('wheel', ['$event'])
  onWheel(event: WheelEvent) {
    event.preventDefault();
    const delta = event.deltaY > 0 ? 0.9 : 1.1;
    const newZoom = Math.min(3, Math.max(0.3, this.zoom() * delta));
    this.zoom.set(newZoom);
  }

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
    this.updateMousePos(event);
  }

  private updateMousePos(event: MouseEvent) {
    const svg = this.svgRef?.nativeElement;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    this.mousePos.set({ x, y });
    const gridX = Math.floor((x / this.zoom() - this.panX()) / this.cellSize);
    const gridY = Math.floor((y / this.zoom() - this.panY()) / this.cellSize);
    this.hoverPos.set({ x: gridX, y: gridY });
  }

  isVisible(pos: GridPos): boolean {
    const selected = this.selectedId();
    if (!selected || !this.map) return true;
    const token = this.tokens.find(t => t.id === selected);
    if (!token) return true;
    const dx = pos.x - token.x;
    const dy = pos.y - token.y;
    const dist = Math.sqrt(dx * dx + dy * dy);
    return dist <= this.visibilityRadius;
  }

  isReachable(pos: GridPos): boolean {
    const cells = this.reachableCells();
    return cells.some(s => s.x === pos.x && s.y === pos.y);
  }

  tokenAt(pos: GridPos) {
    return this.tokens.find(t => t.x === pos.x && t.y === pos.y);
  }

  gridCells(): GridPos[] {
    const cells: GridPos[] = [];
    for (let y = 0; y < this.gridHeight; y++) {
      for (let x = 0; x < this.gridWidth; x++) {
        cells.push({ x, y });
      }
    }
    return cells;
  }

  onCellClick(cell: GridPos) {
    const selected = this.selectedId();
    if (!selected) return;
    if (!this.isReachable(cell)) return;
    const mapId = this.mapIdSignal();
    if (!mapId) return;
    this.moveToken(selected, cell.x, cell.y);
  }

  moveToken(tokenId: string, newX: number, newY: number) {
    const mapId = this.mapIdSignal();
    if (!mapId) return;
    this.facade.moveToken(mapId, tokenId, newX, newY).subscribe({
      next: () => {
        this.facade.loadMap(mapId);
      },
      error: () => {}
    });
  }
}
