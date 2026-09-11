import { Routes } from '@angular/router';
import { FirstRunSetupComponent } from './first-run-setup.component';
import { LoginComponent } from './login.component';
import { ShellComponent } from './shell.component';
import { AuthGuard } from './auth.guard';
import { FirstRunGuard } from './first-run.guard';
import { MembershipManagementComponent } from './membership-management.component';
import { CharacterListComponent } from './character-list.component';
import { CreateCharacterComponent } from './create-character.component';
import { CharacterSheetComponent } from './character-sheet.component';
import { EditCharacterComponent } from './edit-character.component';
import { EncounterSetupComponent } from './encounter-setup.component';
import { EncounterPlayComponent } from './encounter-play.component';

export const routes: Routes = [
  { path: 'setup', component: FirstRunSetupComponent },
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [AuthGuard, FirstRunGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'campaigns/:campaignId/dashboard',
        loadComponent: () => import('./campaign-dashboard.component').then((m) => m.CampaignDashboardComponent),
      },
      {
        path: 'campaigns',
        loadComponent: () => import('./campaign-browser.component').then((m) => m.CampaignBrowserComponent),
      },
      {
        path: 'adventures',
        loadComponent: () => import('./adventure-library.component').then((m) => m.AdventureLibraryComponent),
      },
      {
        path: 'adventures/import',
        loadComponent: () => import('./adventure-import.component').then((m) => m.AdventureImportComponent),
      },
      {
        path: 'adventures/backup',
        loadComponent: () => import('./adventure-backup.component').then((m) => m.AdventureBackupComponent),
      },
      {
        path: 'adventures/export',
        loadComponent: () => import('./adventure-export.component').then((m) => m.AdventureExportComponent),
      },
      {
        path: 'adventures/:id',
        loadComponent: () => import('./adventure-detail.component').then((m) => m.AdventureDetailComponent),
      },
      {
        path: 'create',
        loadComponent: () => import('./create-campaign.component').then((m) => m.CreateCampaignComponent),
      },
      {
        path: 'join',
        loadComponent: () => import('./join-campaign.component').then((m) => m.JoinCampaignComponent),
      },
      {
        path: 'characters',
        loadComponent: () => import('./character-list.component').then((m) => m.CharacterListComponent),
      },
      {
        path: 'characters/create',
        loadComponent: () => import('./create-character.component').then((m) => m.CreateCharacterComponent),
      },
      {
        path: 'characters/:id',
        loadComponent: () => import('./character-sheet.component').then((m) => m.CharacterSheetComponent),
      },
      {
        path: 'characters/:id/edit',
        loadComponent: () => import('./edit-character.component').then((m) => m.EditCharacterComponent),
      },
      {
        path: 'campaigns/:campaignId/members',
        loadComponent: () => import('./membership-management.component').then((m) => m.MembershipManagementComponent),
      },
      {
        path: 'campaigns/:campaignId/invites',
        loadComponent: () => import('./invite-management.component').then((m) => m.InviteManagementComponent),
      },
      {
        path: 'encounters/create',
        loadComponent: () => import('./encounter-setup.component').then((m) => m.EncounterSetupComponent),
      },
      {
        path: 'encounters/:id',
        loadComponent: () => import('./encounter-play.component').then((m) => m.EncounterPlayComponent),
      },
      {
        path: 'encounters/:id/history',
        loadComponent: () => import('./turn-history.component').then((m) => m.TurnHistoryComponent),
      },
      {
        path: 'encounters/:id/audit',
        loadComponent: () => import('./audit-history.component').then((m) => m.AuditHistoryComponent),
      },
      {
        path: 'encounters/:id/map',
        loadComponent: () => import('./tactical-map.component').then((m) => m.TacticalMapComponent),
      },
      {
        path: 'campaigns/:campaignId/narrative',
        loadComponent: () => import('./campaign-narrative.component').then((m) => m.CampaignNarrativeComponent),
      },
      {
        path: 'campaigns/:campaignId/settings',
        loadComponent: () => import('./campaign-settings.component').then((m) => m.CampaignSettingsComponent),
      },
      {
        path: 'profile',
        loadComponent: () => import('./profile.component').then((m) => m.ProfileComponent),
      },
      {
        path: 'password-change',
        loadComponent: () => import('./password-change.component').then((m) => m.PasswordChangeComponent),
      },
      {
        path: 'settings/ai',
        loadComponent: () => import('./ai-endpoint-settings.component').then((m) => m.AiEndpointSettingsComponent),
      },
      {
        path: 'diagnostics',
        loadComponent: () => import('./diagnostics.component').then((m) => m.DiagnosticsComponent),
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
