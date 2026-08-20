# StoryAI Website Backend Agent Rules

## Authoritative role

- This repository is the authoritative backend for the StoryAI customer website in `/Users/zongjei/Documents/code/capcut`.
- It serves the `/api/music-mv/v1/**` website APIs. Local development normally listens on port `8080` and is selected by the frontend's `MUSIC_MV_BACKEND_URL`.
- `aiyingji-houduan` is not the StoryAI website backend. Never implement or redirect StoryAI website project, asset, template-catalog, or render flows there.
- `/Users/zongjei/Documents/code/pengyouquan-web` is the template-management and Mac renderer/research project. It synchronizes published template/runtime metadata into this backend; customer browsers must not call its port `8082` directly.

## Browser rendering direction

- For browser-capable published template versions, issue a browser render session instead of a Mac renderer queue item.
- Keep ownership checks for music, photos, projects, render sessions, and output artifacts in this backend.
- Browser-encoded output should be uploaded to managed storage and registered here so Library and result pages remain cross-device.
