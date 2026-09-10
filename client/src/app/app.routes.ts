import { Routes } from '@angular/router';
import { FirstRunSetupComponent } from './first-run-setup.component';
import { LoginComponent } from './login.component';
import { ShellComponent } from './shell.component';
import { DashboardComponent } from './dashboard.component';
import { AuthGuard } from './auth.guard';
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
    canActivate: [AuthGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
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
        component: CharacterListComponent,
      },
      {
        path: 'characters/create',
        component: CreateCharacterComponent,
      },
      {
        path: 'characters/:id',
        component: CharacterSheetComponent,
      },
      {
        path: 'characters/:id/edit',
        component: EditCharacterComponent,
      },
      {
        path: 'campaigns/:campaignId/members',
        component: MembershipManagementComponent,
      },
      {
        path: 'encounters/create',
        component: EncounterSetupComponent,
      },
      {
        path: 'encounters/:id',
        component: EncounterPlayComponent,
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
