import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './components/shared/sidebar.component';
import { ToastContainerComponent } from './components/shared/toast-container.component';
import { WebSocketService } from './services/websocket.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, ToastContainerComponent],
  template: `
    <div class="app-layout">
      <app-sidebar></app-sidebar>
      <main class="main-content">
        <router-outlet></router-outlet>
      </main>
    </div>
    <app-toast-container></app-toast-container>
  `
})
export class AppComponent implements OnInit {
  constructor(private wsService: WebSocketService) {}

  ngOnInit(): void {
    this.wsService.connect();
  }
}
