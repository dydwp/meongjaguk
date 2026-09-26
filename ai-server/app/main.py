from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from app.route_service import recommend_routes
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(
    title="멍자국 AI 산책로 추천 API"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8081",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class RecommendRequest(BaseModel):
    latitude: float
    longitude: float


@app.get("/")
def health_check():
    return {
        "message": "AI 산책로 추천 서버 실행 중"
    }


@app.post("/api/routes/recommend")
def recommend_route(
    request: RecommendRequest
):
    try:
        routes = recommend_routes(
            latitude=request.latitude,
            longitude=request.longitude,
            top_k=4
        )

        return {
            "latitude": request.latitude,
            "longitude": request.longitude,
            "count": len(routes),
            "routes": routes
        }

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=str(e)
        )