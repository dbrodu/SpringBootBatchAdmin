import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { AppComponent } from './app/app.component';
import { BATCH_ADMIN_API_BASE } from './app/batch-admin.service';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(),
    // Relative to <base href="/batch-admin/">, so it targets the component's own REST API.
    { provide: BATCH_ADMIN_API_BASE, useValue: 'api' },
  ],
}).catch((err) => console.error(err));
